package com.exchange.testing.support;

import com.exchange.matching.models.Order;
import com.exchange.matching.models.OrderSide;
import com.exchange.matching.models.OrderStatus;
import com.exchange.matching.models.OrderType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class TestExchangeEngine implements AutoCloseable {
    private static final BigDecimal DEFAULT_QUOTE_BALANCE = new BigDecimal("100000000.00");
    private static final BigDecimal DEFAULT_BASE_BALANCE = new BigDecimal("100000.00000000");
    private static final String WAL_FILE_NAME = "exchange_test.wal";
    private static final String SNAPSHOT_FILE_NAME = "exchange_test.snapshot";
    private static final String TEMP_SNAPSHOT_FILE_NAME = "exchange_test.snapshot.tmp";

    private final Object stateLock = new Object();
    private final Path walDirectory;
    private final Path snapshotDirectory;
    private final Path walPath;
    private final Path snapshotPath;
    private final Path tempSnapshotPath;
    private final boolean deterministicMode;
    private final boolean autoSeedBalances;
    private final boolean recordSubmitLatencies;
    private final int replayBatchSize;
    private final AtomicLong nextSequence = new AtomicLong();
    private final AtomicLong nextOrderId = new AtomicLong(1_000L);
    private final AtomicLong nextUserId = new AtomicLong(1L);
    private final AtomicLong tradeCount = new AtomicLong();
    private final AtomicLong walWriteCount = new AtomicLong();
    private final int failWalAfterWrites;
    private final DownstreamWorker persistenceWorker;
    private final DownstreamWorker websocketWorker;
    private final ExecutorService snapshotExecutor = Executors.newSingleThreadExecutor();
    private volatile CompletableFuture<Path> lastSnapshotFuture = CompletableFuture.completedFuture(null);
    private volatile long lastSnapshotSequenceId;

    private final Map<String, MatchingBook> books = new HashMap<>();
    private final Map<Long, ManagedOrder> liveOrders = new HashMap<>();
    private final Map<Long, Long> orderSequences = new HashMap<>();
    private final Set<Long> appliedSequences = new LinkedHashSet<>();
    private final Map<String, Long> userAliases = new LinkedHashMap<>();
    private final Map<Long, Map<String, BigDecimal>> balances = new LinkedHashMap<>();
    private final Map<Long, Map<String, BigDecimal>> lockedBalances = new LinkedHashMap<>();
    private final List<TradeRecord> trades = new ArrayList<>();
    private final List<MarketDataDelta> marketDataDeltas = new ArrayList<>();
    private final Map<Long, List<String>> lifecycleTrace = new HashMap<>();
    private final LatencyHistogram submitLatencyHistogram = new LatencyHistogram();

    private TestExchangeEngine(Builder builder) throws IOException {
        this.walDirectory = builder.walDirectory != null ? builder.walDirectory : Files.createTempDirectory("test-wal");
        this.snapshotDirectory = builder.snapshotDirectory != null ? builder.snapshotDirectory : Files.createTempDirectory("test-snapshot");
        Files.createDirectories(walDirectory);
        Files.createDirectories(snapshotDirectory);
        this.walPath = walDirectory.resolve(WAL_FILE_NAME);
        this.snapshotPath = snapshotDirectory.resolve(SNAPSHOT_FILE_NAME);
        this.tempSnapshotPath = snapshotDirectory.resolve(TEMP_SNAPSHOT_FILE_NAME);
        this.deterministicMode = builder.deterministicMode;
        this.autoSeedBalances = builder.autoSeedBalances;
        this.recordSubmitLatencies = builder.recordSubmitLatencies;
        this.replayBatchSize = builder.replayBatchSize;
        this.failWalAfterWrites = builder.failWalAfterWrites;
        this.persistenceWorker = new DownstreamWorker(builder.persistenceQueueCapacity, builder.persistenceDelay);
        this.websocketWorker = new DownstreamWorker(builder.websocketQueueCapacity, builder.websocketDelay);
        this.persistenceWorker.setFailureMode(builder.persistenceFailureMode);
        this.websocketWorker.setFailureMode(builder.websocketFailureMode);
        this.persistenceWorker.start();
        this.websocketWorker.start();
    }

    public static Builder builder() {
        return new Builder();
    }

    public long submitLimitOrder(String symbol, OrderSide side, double quantity, double price, String userAlias) {
        SubmissionResult result = trySubmit(symbol, side, OrderType.LIMIT, quantity, price, userAlias);
        if (!result.accepted()) {
            throw new IllegalStateException(result.reason());
        }
        return result.orderId();
    }

    public SubmissionResult trySubmit(String symbol, OrderSide side, OrderType type, double quantity, double price, String userAlias) {
        long startNs = System.nanoTime();
        synchronized (stateLock) {
            validateOrder(symbol, quantity, price);
            long userId = userIdFor(userAlias);
            ensureFunding(userId, symbol);
            BalanceReservation reservation = reserveBalance(userId, symbol, side, bd(quantity), bd(price));
            if (!reservation.accepted()) {
                return new SubmissionResult(false, -1L, -1L, "Risk rejected order");
            }

            long sequence = nextSequence.incrementAndGet();
            long orderId = nextOrderId.incrementAndGet();
            long timestamp = deterministicMode ? sequence : System.nanoTime();
            Order order = new Order(orderId, userId, symbol, side, type, bd(price), bd(quantity), timestamp, sequence);

            try {
                appendWal(WalRecord.forOrder(order));
            } catch (IOException e) {
                refundReservation(reservation);
                return new SubmissionResult(false, orderId, sequence, "WAL append failed");
            }

            ManagedOrder managedOrder = new ManagedOrder(order, reservation.asset(), reservation.amount());
            liveOrders.put(orderId, managedOrder);
            orderSequences.put(orderId, sequence);
            appliedSequences.add(sequence);
            trace(orderId, "risk");
            trace(orderId, "sequence");
            trace(orderId, "wal");
            applyOrder(managedOrder, true);
            if (recordSubmitLatencies) {
                submitLatencyHistogram.record(System.nanoTime() - startNs);
            }
            return new SubmissionResult(true, orderId, sequence, "accepted");
        }
    }

    public long cancelOrder(long orderId, String userAlias) {
        synchronized (stateLock) {
            ManagedOrder managedOrder = liveOrders.get(orderId);
            if (managedOrder == null) {
                throw new IllegalArgumentException("Unknown order " + orderId);
            }

            long sequence = nextSequence.incrementAndGet();
            try {
                appendWal(WalRecord.forCancel(sequence, managedOrder.order().getOrderId(), managedOrder.order().getUserId(), managedOrder.order().getSymbol()));
            } catch (IOException e) {
                throw new IllegalStateException("WAL append failed during cancellation", e);
            }

            appliedSequences.add(sequence);
            orderSequences.put(orderId, sequence);
            MatchingBook book = books.get(managedOrder.order().getSymbol());
            removeLiveOrder(managedOrder);
            managedOrder.order().cancel();
            releaseLockedAmount(managedOrder);
            lifecycleTrace.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add("cancel");
            BigDecimal remainingAtLevel = book == null
                    ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP)
                    : book.levelQuantity(managedOrder.order().getSide(), managedOrder.order().getPrice());
            publishBookDelta(managedOrder.order().getSymbol(), managedOrder.order().getSide(), managedOrder.order().getPrice(), remainingAtLevel, sequence, "cancel");
            return sequence;
        }
    }

    public void createSnapshot() {
        synchronized (stateLock) {
            EngineSnapshot snapshot = captureSnapshot();
            lastSnapshotFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    writeSnapshot(snapshot);
                    return snapshotPath;
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write snapshot", e);
                }
            }, snapshotExecutor);
        }
    }

    public boolean waitForSnapshotCompletion(long timeout, TimeUnit unit) throws Exception {
        Future<Path> future = lastSnapshotFuture;
        future.get(timeout, unit);
        return Files.exists(snapshotPath);
    }

    public boolean replayFromSnapshot() throws IOException {
        synchronized (stateLock) {
            resetRuntimeState();
            if (Files.exists(tempSnapshotPath)) {
                Files.delete(tempSnapshotPath);
            }
            if (!Files.exists(snapshotPath)) {
                lastSnapshotSequenceId = 0L;
                return false;
            }

            try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(snapshotPath))) {
                EngineSnapshot snapshot = (EngineSnapshot) inputStream.readObject();
                restoreSnapshot(snapshot);
                lastSnapshotSequenceId = snapshot.sequenceId();
                return true;
            } catch (IOException | ClassNotFoundException ex) {
                lastSnapshotSequenceId = 0L;
                resetRuntimeState();
                return false;
            }
        }
    }

    public void replayFromLatestSnapshot() throws IOException {
        replayFromSnapshot();
        replayWalFromLastSnapshot();
    }

    public void replayWalFromLastSnapshot() throws IOException {
        replayWalSince(lastSnapshotSequenceId);
    }

    public void replayWalFromBeginning() throws IOException {
        synchronized (stateLock) {
            resetRuntimeState();
        }
        replayWalSince(0L);
    }

    public void recoverPreferSnapshotThenWal() throws IOException {
        boolean snapshotRecovered = replayFromSnapshot();
        if (!snapshotRecovered) {
            replayWalFromBeginning();
            return;
        }
        replayWalFromLastSnapshot();
    }

    private void replayWalSince(long fromSequenceId) throws IOException {
        if (!Files.exists(walPath)) {
            return;
        }

        List<WalRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(walPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                WalRecord record = WalRecord.parse(line);
                if (record.sequenceId() > fromSequenceId) {
                    records.add(record);
                }
            }
        }

        int processed = 0;
        for (WalRecord record : records) {
            synchronized (stateLock) {
                applyWalRecord(record);
            }
            processed++;
            if (replayBatchSize > 0 && processed % replayBatchSize == 0 && !deterministicMode) {
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void applyWalRecord(WalRecord record) {
        nextSequence.set(Math.max(nextSequence.get(), record.sequenceId()));
        appliedSequences.add(record.sequenceId());
        if (record.kind == WalKind.ORDER) {
            Order order = new Order(
                    record.orderId,
                    record.userId,
                    record.symbol,
                    record.side,
                    record.type,
                    record.price,
                    record.quantity,
                    record.timestamp,
                    record.sequenceId
            );
            ensureFunding(record.userId, record.symbol);
            BalanceReservation reservation = reserveBalance(record.userId, record.symbol, record.side, record.quantity, record.price);
            ManagedOrder managedOrder = new ManagedOrder(order, reservation.asset(), reservation.amount());
            liveOrders.put(order.getOrderId(), managedOrder);
            orderSequences.put(order.getOrderId(), record.sequenceId());
            nextOrderId.set(Math.max(nextOrderId.get(), order.getOrderId()));
            applyOrder(managedOrder, false);
        } else {
            ManagedOrder managedOrder = liveOrders.get(record.orderId);
            if (managedOrder != null) {
                removeLiveOrder(managedOrder);
                managedOrder.order().cancel();
                releaseLockedAmount(managedOrder);
            }
        }
    }

    private void applyOrder(ManagedOrder managedOrder, boolean runtimeSubmission) {
        MatchingBook book = books.computeIfAbsent(managedOrder.order().getSymbol(), ignored -> new MatchingBook());
        Order taker = managedOrder.order();
        OrderSide incomingSide = taker.getSide();

        while (taker.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            ManagedOrder maker = book.peekOpposite(incomingSide);
            if (maker == null || !isCrossing(taker, maker.order())) {
                break;
            }

            BigDecimal matchedQuantity = taker.getRemainingQuantity().min(maker.order().getRemainingQuantity());
            BigDecimal executionPrice = maker.order().getPrice();
            executeTrade(maker, managedOrder, matchedQuantity, executionPrice);

            if (maker.order().isFilled()) {
                book.removeBestOpposite(incomingSide);
                liveOrders.remove(maker.order().getOrderId());
                releaseLockedAmount(maker);
                publishBookDelta(maker.order().getSymbol(), maker.order().getSide(), maker.order().getPrice(), book.levelQuantity(maker.order().getSide(), maker.order().getPrice()), taker.getSequenceId(), "fill");
            }
        }

        if (taker.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0 && taker.getType() == OrderType.LIMIT) {
            book.add(managedOrder);
            publishBookDelta(taker.getSymbol(), taker.getSide(), taker.getPrice(), book.levelQuantity(taker.getSide(), taker.getPrice()), taker.getSequenceId(), runtimeSubmission ? "add" : "replay-add");
        } else if (taker.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            taker.cancel();
            releaseLockedAmount(managedOrder);
            liveOrders.remove(taker.getOrderId());
        } else {
            liveOrders.remove(taker.getOrderId());
            releaseLockedAmount(managedOrder);
        }

        trace(taker.getOrderId(), "match");
        queueDownstreamEvent("persist:" + taker.getOrderId());
    }

    private void executeTrade(ManagedOrder maker, ManagedOrder taker, BigDecimal matchedQuantity, BigDecimal executionPrice) {
        Order makerOrder = maker.order();
        Order takerOrder = taker.order();

        makerOrder.reduceRemainingQuantity(matchedQuantity);
        takerOrder.reduceRemainingQuantity(matchedQuantity);

        ManagedOrder buyer = makerOrder.getSide() == OrderSide.BUY ? maker : taker;
        ManagedOrder seller = makerOrder.getSide() == OrderSide.SELL ? maker : taker;
        BigDecimal quoteAmount = matchedQuantity.multiply(executionPrice).setScale(8, RoundingMode.HALF_UP);

        decrementLocked(buyer, quoteAmount);
        decrementLocked(seller, matchedQuantity);

        credit(buyer.order().getUserId(), assetPair(buyer.order().getSymbol())[0], matchedQuantity);
        credit(seller.order().getUserId(), assetPair(seller.order().getSymbol())[1], quoteAmount);

        tradeCount.incrementAndGet();
        trades.add(new TradeRecord(
                tradeCount.get(),
                takerOrder.getSequenceId(),
                takerOrder.getSymbol(),
                makerOrder.getOrderId(),
                takerOrder.getOrderId(),
                executionPrice,
                matchedQuantity
        ));

        queueDownstreamEvent("trade:" + tradeCount.get());
        publishBookDelta(takerOrder.getSymbol(), makerOrder.getSide(), makerOrder.getPrice(), books.get(takerOrder.getSymbol()).levelQuantity(makerOrder.getSide(), makerOrder.getPrice()), takerOrder.getSequenceId(), "trade");
    }

    private void queueDownstreamEvent(String payload) {
        persistenceWorker.offer(payload);
        websocketWorker.offer(payload);
    }

    private void publishBookDelta(String symbol, OrderSide side, BigDecimal price, BigDecimal remainingLevelQuantity, long sequenceId, String reason) {
        MarketDataDelta delta = new MarketDataDelta(symbol, side, price, remainingLevelQuantity, sequenceId, reason);
        marketDataDeltas.add(delta);
        websocketWorker.offer(delta);
    }

    private void trace(long orderId, String stage) {
        lifecycleTrace.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(stage);
    }

    private boolean isCrossing(Order taker, Order maker) {
        if (taker.getSide() == OrderSide.BUY) {
            return taker.getPrice().compareTo(maker.getPrice()) >= 0;
        }
        return taker.getPrice().compareTo(maker.getPrice()) <= 0;
    }

    private BalanceReservation reserveBalance(long userId, String symbol, OrderSide side, BigDecimal quantity, BigDecimal price) {
        String[] pair = assetPair(symbol);
        String base = pair[0];
        String quote = pair[1];
        String asset = side == OrderSide.BUY ? quote : base;
        BigDecimal amount = side == OrderSide.BUY ? quantity.multiply(price).setScale(8, RoundingMode.HALF_UP) : quantity;
        Map<String, BigDecimal> wallet = balances.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
        BigDecimal available = wallet.getOrDefault(asset, BigDecimal.ZERO);
        if (available.compareTo(amount) < 0) {
            return new BalanceReservation(false, userId, asset, amount);
        }
        wallet.put(asset, available.subtract(amount));
        lockedBalances.computeIfAbsent(userId, ignored -> new LinkedHashMap<>())
                .merge(asset, amount, BigDecimal::add);
        return new BalanceReservation(true, userId, asset, amount);
    }

    private void refundReservation(BalanceReservation reservation) {
        if (!reservation.accepted()) {
            return;
        }
        balances.computeIfAbsent(reservation.userId(), ignored -> new LinkedHashMap<>())
                .merge(reservation.asset(), reservation.amount(), BigDecimal::add);
        lockedBalances.computeIfAbsent(reservation.userId(), ignored -> new LinkedHashMap<>())
                .merge(reservation.asset(), reservation.amount().negate(), BigDecimal::add);
    }

    private void releaseLockedAmount(ManagedOrder order) {
        if (order.lockedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        credit(order.order().getUserId(), order.lockedAsset, order.lockedAmount);
        Map<String, BigDecimal> lockedWallet = lockedBalances.get(order.order().getUserId());
        if (lockedWallet != null) {
            lockedWallet.merge(order.lockedAsset, order.lockedAmount.negate(), BigDecimal::add);
        }
        order.lockedAmount = BigDecimal.ZERO;
    }

    private void decrementLocked(ManagedOrder order, BigDecimal amount) {
        order.lockedAmount = order.lockedAmount.subtract(amount);
        Map<String, BigDecimal> lockedWallet = lockedBalances.get(order.order().getUserId());
        if (lockedWallet != null) {
            lockedWallet.merge(order.lockedAsset, amount.negate(), BigDecimal::add);
        }
    }

    private void credit(long userId, String asset, BigDecimal amount) {
        balances.computeIfAbsent(userId, ignored -> new LinkedHashMap<>()).merge(asset, amount, BigDecimal::add);
    }

    private void ensureFunding(long userId, String symbol) {
        if (!autoSeedBalances) {
            balances.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
            lockedBalances.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
            return;
        }

        String[] pair = assetPair(symbol);
        seedBalance(userId, pair[0], DEFAULT_BASE_BALANCE);
        seedBalance(userId, pair[1], DEFAULT_QUOTE_BALANCE);
    }

    public void seedBalance(String userAlias, String asset, BigDecimal amount) {
        seedBalance(userIdFor(userAlias), asset, amount);
    }

    public void seedBalance(long userId, String asset, BigDecimal amount) {
        balances.computeIfAbsent(userId, ignored -> new LinkedHashMap<>()).putIfAbsent(asset, amount);
        lockedBalances.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
    }

    private long userIdFor(String userAlias) {
        return userAliases.computeIfAbsent(userAlias, ignored -> nextUserId.getAndIncrement());
    }

    private void removeLiveOrder(ManagedOrder order) {
        MatchingBook book = books.get(order.order().getSymbol());
        if (book != null) {
            book.remove(order);
        }
        liveOrders.remove(order.order().getOrderId());
    }

    private void appendWal(WalRecord record) throws IOException {
        long writes = walWriteCount.incrementAndGet();
        if (writes > failWalAfterWrites) {
            throw new IOException("Injected WAL failure");
        }
        Files.createDirectories(walDirectory);
        try (BufferedWriter writer = Files.newBufferedWriter(walPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(record.serialize());
            writer.newLine();
        }
    }

    private EngineSnapshot captureSnapshot() {
        Map<String, List<OrderState>> orderStates = new LinkedHashMap<>();
        for (Map.Entry<String, MatchingBook> entry : books.entrySet()) {
            orderStates.put(entry.getKey(), entry.getValue().snapshot());
        }
        return new EngineSnapshot(
                nextSequence.get(),
                nextOrderId.get(),
                nextUserId.get(),
                copyBalances(balances),
                copyBalances(lockedBalances),
                new LinkedHashMap<>(userAliases),
                orderStates,
                new ArrayList<>(trades),
                new LinkedHashMap<>(orderSequences),
                new LinkedHashSet<>(appliedSequences)
        );
    }

    private void writeSnapshot(EngineSnapshot snapshot) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(snapshot);
        }
        Files.write(tempSnapshotPath, bytes.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tempSnapshotPath, snapshotPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        lastSnapshotSequenceId = snapshot.sequenceId();
    }

    private void restoreSnapshot(EngineSnapshot snapshot) {
        resetRuntimeState();
        nextSequence.set(snapshot.sequenceId());
        nextOrderId.set(snapshot.nextOrderId());
        nextUserId.set(snapshot.nextUserId());
        balances.putAll(copyBalances(snapshot.balances()));
        lockedBalances.putAll(copyBalances(snapshot.lockedBalances()));
        userAliases.putAll(snapshot.userAliases());
        trades.addAll(snapshot.trades());
        tradeCount.set(snapshot.trades().size());
        orderSequences.putAll(snapshot.orderSequences());
        appliedSequences.addAll(snapshot.appliedSequences());
        for (Map.Entry<String, List<OrderState>> entry : snapshot.openOrders().entrySet()) {
            MatchingBook book = books.computeIfAbsent(entry.getKey(), ignored -> new MatchingBook());
            for (OrderState state : entry.getValue()) {
                Order order = state.toOrder();
                ManagedOrder managedOrder = new ManagedOrder(order, state.lockedAsset(), state.lockedAmount());
                managedOrder.lockedAmount = state.lockedAmount();
                liveOrders.put(order.getOrderId(), managedOrder);
                book.add(managedOrder);
            }
        }
    }

    private void resetRuntimeState() {
        books.clear();
        liveOrders.clear();
        orderSequences.clear();
        appliedSequences.clear();
        balances.clear();
        lockedBalances.clear();
        userAliases.clear();
        trades.clear();
        marketDataDeltas.clear();
        lifecycleTrace.clear();
        tradeCount.set(0L);
        nextSequence.set(0L);
        nextOrderId.set(1_000L);
        nextUserId.set(1L);
        lastSnapshotSequenceId = 0L;
    }

    private Map<Long, Map<String, BigDecimal>> copyBalances(Map<Long, Map<String, BigDecimal>> source) {
        Map<Long, Map<String, BigDecimal>> copy = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, BigDecimal>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    public byte[] captureStateHash() {
        synchronized (stateLock) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                updateDigest(digest, "seq=" + nextSequence.get());
                for (String symbol : new TreeMap<>(books).keySet()) {
                    updateDigest(digest, symbol);
                    for (OrderState state : books.get(symbol).snapshot()) {
                        updateDigest(digest, state.toString());
                    }
                }
                for (Map.Entry<Long, Map<String, BigDecimal>> entry : new TreeMap<>(balances).entrySet()) {
                    updateDigest(digest, entry.getKey().toString());
                    for (Map.Entry<String, BigDecimal> balance : new TreeMap<>(entry.getValue()).entrySet()) {
                        updateDigest(digest, balance.getKey() + "=" + balance.getValue());
                    }
                }
                for (TradeRecord trade : trades) {
                    updateDigest(digest, trade.toString());
                }
                return digest.digest();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to hash engine state", e);
            }
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    public BookDepthSnapshot getDepth(String symbol, int levels) {
        synchronized (stateLock) {
            MatchingBook book = books.get(symbol);
            return book == null ? BookDepthSnapshot.empty(symbol) : book.depth(symbol, levels);
        }
    }

    public long getTradeCount() {
        return tradeCount.get();
    }

    public long getGlobalSequenceId() {
        return nextSequence.get();
    }

    public long getOrderSequenceId(long orderId) {
        synchronized (stateLock) {
            return orderSequences.get(orderId);
        }
    }

    public boolean hasSequenceId(long sequenceId) {
        synchronized (stateLock) {
            return appliedSequences.contains(sequenceId);
        }
    }

    public Map<Long, Map<String, BigDecimal>> getAllAccountBalances() {
        synchronized (stateLock) {
            return copyBalances(balances);
        }
    }

    public List<Order> getAllPendingOrders() {
        synchronized (stateLock) {
            return liveOrders.values().stream()
                    .map(ManagedOrder::order)
                    .sorted(Comparator.comparingLong(Order::getOrderId))
                    .toList();
        }
    }

    public List<TradeRecord> getTrades() {
        synchronized (stateLock) {
            return List.copyOf(trades);
        }
    }

    public List<MarketDataDelta> getMarketDataDeltas() {
        synchronized (stateLock) {
            return List.copyOf(marketDataDeltas);
        }
    }

    public List<String> getLifecycleTrace(long orderId) {
        synchronized (stateLock) {
            return List.copyOf(lifecycleTrace.getOrDefault(orderId, List.of()));
        }
    }

    public LatencyHistogram submitLatencyHistogram() {
        return submitLatencyHistogram;
    }

    public Path getWalPath() {
        return walPath;
    }

    public Path getSnapshotPath() {
        return snapshotPath;
    }

    public Path getTempSnapshotPath() {
        return tempSnapshotPath;
    }

    public long getPersistenceBacklog() {
        return persistenceWorker.backlog();
    }

    public long getWebsocketBacklog() {
        return websocketWorker.backlog();
    }

    public long getPersistenceDrops() {
        return persistenceWorker.dropped();
    }

    public long getWebsocketDrops() {
        return websocketWorker.dropped();
    }

    public long getPersistenceProcessed() {
        return persistenceWorker.processed();
    }

    public long getWebsocketProcessed() {
        return websocketWorker.processed();
    }

    public void pausePersistenceConsumer() {
        persistenceWorker.setPaused(true);
    }

    public void resumePersistenceConsumer() {
        persistenceWorker.setPaused(false);
    }

    public void pauseWebsocketConsumer() {
        websocketWorker.setPaused(true);
    }

    public void resumeWebsocketConsumer() {
        websocketWorker.setPaused(false);
    }

    public void setPersistenceFailureMode(boolean failureMode) {
        persistenceWorker.setFailureMode(failureMode);
    }

    public void setWebsocketFailureMode(boolean failureMode) {
        websocketWorker.setFailureMode(failureMode);
    }

    public void setPersistenceDelay(Duration delay) {
        persistenceWorker.setDelay(delay);
    }

    public void setWebsocketDelay(Duration delay) {
        websocketWorker.setDelay(delay);
    }

    public void createOrphanedTempSnapshot(byte[] payload) throws IOException {
        Files.write(tempSnapshotPath, payload, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void corruptActiveSnapshot() throws IOException {
        Files.writeString(snapshotPath, "corrupt", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void truncateActiveSnapshot(int bytes) throws IOException {
        byte[] current = Files.readAllBytes(snapshotPath);
        int size = Math.max(0, Math.min(bytes, current.length));
        Files.write(snapshotPath, copyOf(current, size), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private byte[] copyOf(byte[] source, int size) {
        byte[] copy = new byte[size];
        System.arraycopy(source, 0, copy, 0, size);
        return copy;
    }

    public void appendCorruptedWalLine(String line) throws IOException {
        Files.writeString(walPath, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void waitForMatching() {
        // Matching is synchronous in the deterministic test engine.
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        persistenceWorker.stop();
        websocketWorker.stop();
        snapshotExecutor.shutdownNow();
    }

    private void validateOrder(String symbol, double quantity, double price) {
        if (symbol == null || assetPair(symbol).length != 2) {
            throw new IllegalArgumentException("Unsupported symbol format");
        }
        if (!(quantity > 0.0d) || Double.isNaN(quantity) || Double.isInfinite(quantity)) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (!(price > 0.0d) || Double.isNaN(price) || Double.isInfinite(price)) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    private String[] assetPair(String symbol) {
        return symbol.split("[-_/]");
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }

    public record SubmissionResult(boolean accepted, long orderId, long sequenceId, String reason) {
    }

    public record PriceLevel(BigDecimal price, BigDecimal quantity, int orderCount) implements Serializable {
    }

    public record BookDepthSnapshot(String symbol, List<PriceLevel> bids, List<PriceLevel> asks) implements Serializable {
        static BookDepthSnapshot empty(String symbol) {
            return new BookDepthSnapshot(symbol, List.of(), List.of());
        }
    }

    public record TradeRecord(
            long tradeId,
            long sequenceId,
            String symbol,
            long makerOrderId,
            long takerOrderId,
            BigDecimal price,
            BigDecimal quantity
    ) implements Serializable {
    }

    public record MarketDataDelta(
            String symbol,
            OrderSide side,
            BigDecimal price,
            BigDecimal totalQuantityAtLevel,
            long sequenceId,
            String reason
    ) implements Serializable {
    }

    public static final class Builder {
        private Path walDirectory;
        private Path snapshotDirectory;
        private boolean deterministicMode = true;
        private boolean autoSeedBalances = true;
        private boolean recordSubmitLatencies = true;
        private int replayBatchSize = 256;
        private int failWalAfterWrites = Integer.MAX_VALUE;
        private int persistenceQueueCapacity = 8_192;
        private int websocketQueueCapacity = 8_192;
        private Duration persistenceDelay = Duration.ZERO;
        private Duration websocketDelay = Duration.ZERO;
        private boolean persistenceFailureMode;
        private boolean websocketFailureMode;

        public Builder walDirectory(Path walDirectory) {
            this.walDirectory = walDirectory;
            return this;
        }

        public Builder snapshotDirectory(Path snapshotDirectory) {
            this.snapshotDirectory = snapshotDirectory;
            return this;
        }

        public Builder enableDeterministicMode(boolean deterministicMode) {
            this.deterministicMode = deterministicMode;
            return this;
        }

        public Builder autoSeedBalances(boolean autoSeedBalances) {
            this.autoSeedBalances = autoSeedBalances;
            return this;
        }

        public Builder recordSubmitLatencies(boolean recordSubmitLatencies) {
            this.recordSubmitLatencies = recordSubmitLatencies;
            return this;
        }

        public Builder replayBatchSize(int replayBatchSize) {
            this.replayBatchSize = replayBatchSize;
            return this;
        }

        public Builder failWalAfterWrites(int failWalAfterWrites) {
            this.failWalAfterWrites = failWalAfterWrites;
            return this;
        }

        public Builder persistenceQueueCapacity(int persistenceQueueCapacity) {
            this.persistenceQueueCapacity = persistenceQueueCapacity;
            return this;
        }

        public Builder websocketQueueCapacity(int websocketQueueCapacity) {
            this.websocketQueueCapacity = websocketQueueCapacity;
            return this;
        }

        public Builder persistenceDelay(Duration persistenceDelay) {
            this.persistenceDelay = persistenceDelay;
            return this;
        }

        public Builder websocketDelay(Duration websocketDelay) {
            this.websocketDelay = websocketDelay;
            return this;
        }

        public Builder persistenceFailureMode(boolean persistenceFailureMode) {
            this.persistenceFailureMode = persistenceFailureMode;
            return this;
        }

        public Builder websocketFailureMode(boolean websocketFailureMode) {
            this.websocketFailureMode = websocketFailureMode;
            return this;
        }

        public TestExchangeEngine build() throws IOException {
            return new TestExchangeEngine(this);
        }
    }

    private record BalanceReservation(boolean accepted, long userId, String asset, BigDecimal amount) {
    }

    private enum WalKind {
        ORDER,
        CANCEL
    }

    private record WalRecord(
            WalKind kind,
            long sequenceId,
            long orderId,
            long userId,
            String symbol,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity,
            long timestamp
    ) {
        static WalRecord forOrder(Order order) {
            return new WalRecord(
                    WalKind.ORDER,
                    order.getSequenceId(),
                    order.getOrderId(),
                    order.getUserId(),
                    order.getSymbol(),
                    order.getSide(),
                    order.getType(),
                    order.getPrice(),
                    order.getQuantity(),
                    order.getTimestamp()
            );
        }

        static WalRecord forCancel(long sequenceId, long orderId, long userId, String symbol) {
            return new WalRecord(WalKind.CANCEL, sequenceId, orderId, userId, symbol, null, null, BigDecimal.ZERO, BigDecimal.ZERO, sequenceId);
        }

        String serialize() {
            return String.join("|",
                    kind.name(),
                    String.valueOf(sequenceId),
                    String.valueOf(orderId),
                    String.valueOf(userId),
                    symbol,
                    side == null ? "" : side.name(),
                    type == null ? "" : type.name(),
                    price.toPlainString(),
                    quantity.toPlainString(),
                    String.valueOf(timestamp));
        }

        static WalRecord parse(String line) {
            String[] parts = line.split("\\|");
            if (parts.length < 10) {
                throw new IllegalArgumentException("Malformed WAL entry: " + line);
            }
            WalKind kind = WalKind.valueOf(parts[0]);
            return new WalRecord(
                    kind,
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]),
                    Long.parseLong(parts[3]),
                    parts[4],
                    parts[5].isBlank() ? null : OrderSide.valueOf(parts[5]),
                    parts[6].isBlank() ? null : OrderType.valueOf(parts[6]),
                    new BigDecimal(parts[7]),
                    new BigDecimal(parts[8]),
                    Long.parseLong(parts[9])
            );
        }
    }

    private record OrderState(
            long orderId,
            long userId,
            String symbol,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal remainingQuantity,
            long timestamp,
            long sequenceId,
            OrderStatus status,
            String lockedAsset,
            BigDecimal lockedAmount
    ) implements Serializable {
        Order toOrder() {
            Order order = new Order(orderId, userId, symbol, side, type, price, quantity, timestamp, sequenceId);
            BigDecimal executed = quantity.subtract(remainingQuantity);
            if (executed.compareTo(BigDecimal.ZERO) > 0) {
                order.reduceRemainingQuantity(executed);
            }
            if (status == OrderStatus.CANCELLED) {
                order.cancel();
            }
            return order;
        }
    }

    private record EngineSnapshot(
            long sequenceId,
            long nextOrderId,
            long nextUserId,
            Map<Long, Map<String, BigDecimal>> balances,
            Map<Long, Map<String, BigDecimal>> lockedBalances,
            Map<String, Long> userAliases,
            Map<String, List<OrderState>> openOrders,
            List<TradeRecord> trades,
            Map<Long, Long> orderSequences,
            Set<Long> appliedSequences
    ) implements Serializable {
    }

    private static final class ManagedOrder {
        private final Order order;
        private final String lockedAsset;
        private BigDecimal lockedAmount;

        private ManagedOrder(Order order, String lockedAsset, BigDecimal lockedAmount) {
            this.order = order;
            this.lockedAsset = lockedAsset;
            this.lockedAmount = lockedAmount;
        }

        private Order order() {
            return order;
        }
    }

    private static final class MatchingBook {
        private final NavigableMap<BigDecimal, Deque<ManagedOrder>> bids = new TreeMap<>(Comparator.reverseOrder());
        private final NavigableMap<BigDecimal, Deque<ManagedOrder>> asks = new TreeMap<>();

        private void add(ManagedOrder order) {
            side(order.order().getSide()).computeIfAbsent(order.order().getPrice(), ignored -> new ArrayDeque<>()).addLast(order);
        }

        private void remove(ManagedOrder order) {
            NavigableMap<BigDecimal, Deque<ManagedOrder>> side = side(order.order().getSide());
            Deque<ManagedOrder> queue = side.get(order.order().getPrice());
            if (queue == null) {
                return;
            }
            queue.removeIf(candidate -> candidate.order().getOrderId() == order.order().getOrderId());
            if (queue.isEmpty()) {
                side.remove(order.order().getPrice());
            }
        }

        private ManagedOrder peekOpposite(OrderSide side) {
            return first(side == OrderSide.BUY ? asks : bids);
        }

        private void removeBestOpposite(OrderSide side) {
            pop(side == OrderSide.BUY ? asks : bids);
        }

        private BigDecimal levelQuantity(OrderSide side, BigDecimal price) {
            Deque<ManagedOrder> queue = side(side).get(price);
            if (queue == null) {
                return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
            }
            BigDecimal total = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
            for (ManagedOrder order : queue) {
                total = total.add(order.order().getRemainingQuantity());
            }
            return total;
        }

        private List<OrderState> snapshot() {
            List<OrderState> states = new ArrayList<>();
            snapshotSide(states, bids);
            snapshotSide(states, asks);
            states.sort(Comparator.comparingLong(OrderState::sequenceId));
            return states;
        }

        private void snapshotSide(List<OrderState> states, NavigableMap<BigDecimal, Deque<ManagedOrder>> side) {
            for (Deque<ManagedOrder> queue : side.values()) {
                for (ManagedOrder order : queue) {
                    states.add(new OrderState(
                            order.order().getOrderId(),
                            order.order().getUserId(),
                            order.order().getSymbol(),
                            order.order().getSide(),
                            order.order().getType(),
                            order.order().getPrice(),
                            order.order().getQuantity(),
                            order.order().getRemainingQuantity(),
                            order.order().getTimestamp(),
                            order.order().getSequenceId(),
                            order.order().getStatus(),
                            order.lockedAsset,
                            order.lockedAmount
                    ));
                }
            }
        }

        private BookDepthSnapshot depth(String symbol, int levels) {
            return new BookDepthSnapshot(symbol, topLevels(bids, levels), topLevels(asks, levels));
        }

        private List<PriceLevel> topLevels(NavigableMap<BigDecimal, Deque<ManagedOrder>> side, int levels) {
            List<PriceLevel> snapshot = new ArrayList<>();
            for (Map.Entry<BigDecimal, Deque<ManagedOrder>> entry : side.entrySet()) {
                if (snapshot.size() >= levels) {
                    break;
                }
                BigDecimal quantity = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
                for (ManagedOrder order : entry.getValue()) {
                    quantity = quantity.add(order.order().getRemainingQuantity());
                }
                snapshot.add(new PriceLevel(entry.getKey(), quantity, entry.getValue().size()));
            }
            return snapshot;
        }

        private NavigableMap<BigDecimal, Deque<ManagedOrder>> side(OrderSide side) {
            return side == OrderSide.BUY ? bids : asks;
        }

        private ManagedOrder first(NavigableMap<BigDecimal, Deque<ManagedOrder>> side) {
            if (side.isEmpty()) {
                return null;
            }
            Deque<ManagedOrder> queue = side.firstEntry().getValue();
            return queue.peekFirst();
        }

        private void pop(NavigableMap<BigDecimal, Deque<ManagedOrder>> side) {
            if (side.isEmpty()) {
                return;
            }
            Map.Entry<BigDecimal, Deque<ManagedOrder>> entry = side.firstEntry();
            entry.getValue().pollFirst();
            if (entry.getValue().isEmpty()) {
                side.remove(entry.getKey());
            }
        }
    }

    private static final class DownstreamWorker {
        private final ArrayBlockingQueue<Object> queue;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicBoolean failureMode = new AtomicBoolean(false);
        private final AtomicLong processed = new AtomicLong();
        private final AtomicLong dropped = new AtomicLong();
        private volatile Duration delay;
        private Thread workerThread;

        private DownstreamWorker(int capacity, Duration delay) {
            this.queue = new ArrayBlockingQueue<>(capacity);
            this.delay = delay;
        }

        private void start() {
            workerThread = new Thread(() -> {
                while (running.get() || !queue.isEmpty()) {
                    try {
                        Object event = queue.poll(50, TimeUnit.MILLISECONDS);
                        if (event == null) {
                            continue;
                        }
                        while (paused.get() && running.get()) {
                            Thread.sleep(5L);
                        }
                        if (!delay.isZero()) {
                            Thread.sleep(delay.toMillis());
                        }
                        if (failureMode.get()) {
                            continue;
                        }
                        processed.incrementAndGet();
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "test-downstream-worker");
            workerThread.setDaemon(true);
            workerThread.start();
        }

        private void offer(Object event) {
            if (!queue.offer(event)) {
                dropped.incrementAndGet();
            }
        }

        private long backlog() {
            return queue.size();
        }

        private long dropped() {
            return dropped.get();
        }

        private long processed() {
            return processed.get();
        }

        private void setPaused(boolean paused) {
            this.paused.set(paused);
        }

        private void setDelay(Duration delay) {
            this.delay = delay;
        }

        private void setFailureMode(boolean failureMode) {
            this.failureMode.set(failureMode);
        }

        private void stop() {
            running.set(false);
            if (workerThread != null) {
                workerThread.interrupt();
            }
        }
    }
}
