package com.exchange.storage.postgres;

import com.exchange.common.config.ExchangeConfig;
import com.exchange.matching.models.Order;
import com.exchange.trade.execution.Trade;
import com.exchange.observability.metrics.LatencyObservabilityFramework;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous, batch-oriented persistence worker.
 */
public class BatchPersistenceWorker implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(BatchPersistenceWorker.class.getName());

    private final BlockingQueue<Object> eventQueue = new LinkedBlockingQueue<>(500_000);
    private final List<Trade> tradeBatch = new ArrayList<>();
    private final List<Order> orderBatch = new ArrayList<>();

    private final int batchSize;
    private final long flushIntervalMs;
    private final LatencyObservabilityFramework metrics;
    private final NeonDbConnectionManager dbManager;

    private volatile boolean isRunning = true;

    public BatchPersistenceWorker(ExchangeConfig config, LatencyObservabilityFramework metrics, NeonDbConnectionManager dbManager) {
        this.batchSize = config.getDatabase().getBatchThreshold();
        this.flushIntervalMs = config.getDatabase().getFlushIntervalMs();
        this.metrics = metrics;
        this.dbManager = dbManager;
    }

    public void queueForPersistence(Object event) {
        if (!eventQueue.offer(event)) {
            LOGGER.severe("CRITICAL: Persistence queue full! Dropping historical event.");
        }
        metrics.recordPersistenceQueueDepth(eventQueue.size());
    }

    public void stop() {
        isRunning = false;
    }

    @Override
    public void run() {
        long lastFlushTime = System.currentTimeMillis();

        while (isRunning || !eventQueue.isEmpty()) {
            try {
                Object event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    if (event instanceof Trade) tradeBatch.add((Trade) event);
                    else if (event instanceof Order) orderBatch.add((Order) event);
                }

                long now = System.currentTimeMillis();
                if (tradeBatch.size() >= batchSize || orderBatch.size() >= batchSize || (now - lastFlushTime) >= flushIntervalMs) {
                    if (!tradeBatch.isEmpty() || !orderBatch.isEmpty()) {
                        flushBatches();
                        lastFlushTime = now;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error in persistence worker", e);
            }
        }
    }

    private void flushBatches() {
        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false); 

            if (!tradeBatch.isEmpty()) {
                insertTrades(conn, tradeBatch);
                tradeBatch.clear();
            }

            if (!orderBatch.isEmpty()) {
                insertOrders(conn, orderBatch);
                orderBatch.clear();
            }

            conn.commit(); 
            metrics.recordPersistenceLag(eventQueue.size());
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database batch insertion failed.", e);
            handleDeadLetters();
        }
    }

    private void insertTrades(Connection conn, List<Trade> trades) throws SQLException {
        String sql = "INSERT INTO trade_history (trade_id, symbol, maker_order_id, taker_order_id, price, quantity, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Trade trade : trades) {
                pstmt.setLong(1, trade.getTradeId());
                pstmt.setString(2, trade.getSymbol());
                pstmt.setLong(3, trade.getMakerOrderId());
                pstmt.setLong(4, trade.getTakerOrderId());
                pstmt.setBigDecimal(5, trade.getPrice());
                pstmt.setBigDecimal(6, trade.getQuantity());
                pstmt.setLong(7, trade.getTimestamp());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private void insertOrders(Connection conn, List<Order> orders) throws SQLException {
        String sql = "INSERT INTO order_history (order_id, sequence_id, user_id, symbol, side, type, price, quantity, status, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Order order : orders) {
                pstmt.setLong(1, order.getOrderId());
                pstmt.setLong(2, order.getSequenceId());
                pstmt.setLong(3, order.getUserId());
                pstmt.setString(4, order.getSymbol());
                pstmt.setString(5, order.getSide().name());
                pstmt.setString(6, order.getType().name());
                pstmt.setBigDecimal(7, order.getPrice());
                pstmt.setBigDecimal(8, order.getQuantity());
                pstmt.setString(9, order.getStatus().name());
                pstmt.setLong(10, order.getTimestamp());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private void handleDeadLetters() {
        tradeBatch.clear();
        orderBatch.clear();
    }
}
