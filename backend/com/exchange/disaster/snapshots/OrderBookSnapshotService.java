package com.exchange.disaster.snapshots;

import com.exchange.common.config.ExchangeConfig;
import com.exchange.matching.orderbook.OrderBook;
import com.exchange.observability.metrics.LatencyObservabilityFramework;
import com.exchange.order.sequence.GlobalSequencer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

/**
 * Deterministic low-overhead rotating snapshot architecture.
 * Employs async copy-on-write, atomic file replacements (.tmp to .dat), 
 * and embedded metadata/checksums for absolute crash safety.
 */
public class OrderBookSnapshotService {

    private static final Logger LOGGER = Logger.getLogger(OrderBookSnapshotService.class.getName());
    private static final String SNAPSHOT_FILE_NAME = "exchange_snapshot.dat";
    private static final String TEMP_SNAPSHOT_FILE_NAME = "exchange_snapshot.tmp";
    private static final int ENGINE_VERSION = 1;
    private static final String COMPAT_MARKER = "HFT-CORE-V1";

    private final Path snapshotPath;
    private final Path tempSnapshotPath;

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    
    private final GlobalSequencer globalSequencer;
    private final LatencyObservabilityFramework latencyMetrics;

    public OrderBookSnapshotService(GlobalSequencer globalSequencer, LatencyObservabilityFramework latencyMetrics, ExchangeConfig config) {
        this.globalSequencer = globalSequencer;
        this.latencyMetrics = latencyMetrics;

        String snapshotDir = config.getWal().getDirectory(); // Reusing WAL dir or dedicate a snapshot dir
        File dir = new File(snapshotDir);
        if (!dir.exists()) dir.mkdirs();

        this.snapshotPath = Paths.get(snapshotDir, SNAPSHOT_FILE_NAME);
        this.tempSnapshotPath = Paths.get(snapshotDir, TEMP_SNAPSHOT_FILE_NAME);

        int interval = config.getTuning().getSnapshotIntervalSeconds();
        // Coarse-grained checkpointing
        scheduler.scheduleAtFixedRate(this::triggerAsyncSnapshot, interval, interval, TimeUnit.SECONDS);
    }

    public void registerOrderBook(String symbol, OrderBook orderBook) {
        orderBooks.put(symbol, orderBook);
    }

    private void triggerAsyncSnapshot() {
        // Step 1: Capture sequence ID & create shallow copy of references instantaneously
        long snapshotSequenceId = globalSequencer.getCurrentId();
        Map<String, OrderBook> cowMap = new ConcurrentHashMap<>(orderBooks);

        // Step 2: Offload to I/O thread
        ioExecutor.submit(() -> writeSnapshotToDisk(snapshotSequenceId, cowMap));
    }

    private void writeSnapshotToDisk(long sequenceId, Map<String, OrderBook> snapshotData) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Serialize to byte array first to compute CRC32 Checksum
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(snapshotData);
            }
            byte[] payloadBytes = baos.toByteArray();
            
            CRC32 crc = new CRC32();
            crc.update(payloadBytes);
            long checksum = crc.getValue();

            SnapshotMetadata metadata = new SnapshotMetadata(
                    sequenceId,
                    System.currentTimeMillis(),
                    ENGINE_VERSION,
                    checksum,
                    COMPAT_MARKER,
                    snapshotData.size()
            );

            // Write metadata + payload into the temporary file first
            try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tempSnapshotPath.toFile())))) {
                oos.writeObject(metadata);
                oos.write(payloadBytes); // write raw bytes
                oos.flush();
            }

            // Step 3: Atomic move. Ensures we never have a corrupted half-written snapshot.
            Files.move(tempSnapshotPath, snapshotPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            long duration = System.currentTimeMillis() - startTime;
            long size = snapshotPath.toFile().length();
            latencyMetrics.recordSnapshotMetrics(duration, size);
            
            LOGGER.info(String.format("Atomic Snapshot Generation Complete | Seq: %d | Size: %d bytes | Duration: %d ms", sequenceId, size, duration));

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to perform atomic order book snapshot", e);
        }
    }

    @SuppressWarnings("unchecked")
    public RecoveredSnapshot recoverFromSnapshot() {
        // Cleanup stale temp file if the server crashed mid-snapshot
        if (Files.exists(tempSnapshotPath)) {
            try {
                Files.delete(tempSnapshotPath);
                LOGGER.info("Cleaned up orphaned temporary snapshot file.");
            } catch (IOException e) {
                LOGGER.warning("Could not delete temporary snapshot file: " + e.getMessage());
            }
        }

        if (!Files.exists(snapshotPath)) {
            LOGGER.info("No valid snapshot found. Bootstrapping fresh engine.");
            return new RecoveredSnapshot(0L, false);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(snapshotPath.toFile())))) {
            SnapshotMetadata metadata = (SnapshotMetadata) ois.readObject();
            
            // Validate Engine Version & Compatibility Marker
            if (metadata.getEngineVersion() != ENGINE_VERSION || !COMPAT_MARKER.equals(metadata.getCompatibilityMarker())) {
                LOGGER.severe("CRITICAL: Snapshot compatibility mismatch! Expected V" + ENGINE_VERSION + " but found V" + metadata.getEngineVersion());
                return new RecoveredSnapshot(0L, false);
            }

            // Read Payload
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = ois.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            byte[] payloadBytes = baos.toByteArray();

            // Validate Checksum
            CRC32 crc = new CRC32();
            crc.update(payloadBytes);
            if (crc.getValue() != metadata.getChecksum()) {
                LOGGER.severe("CRITICAL: Snapshot checksum validation FAILED. Data is corrupt.");
                return new RecoveredSnapshot(0L, false);
            }

            // Deserialize Map
            try (ObjectInputStream mapOis = new ObjectInputStream(new ByteArrayInputStream(payloadBytes))) {
                Map<String, OrderBook> recoveredBooks = (Map<String, OrderBook>) mapOis.readObject();
                orderBooks.clear();
                orderBooks.putAll(recoveredBooks);
            }

            LOGGER.info("Successfully recovered validated snapshot. Sequence ID: " + metadata.getSequenceId());
            return new RecoveredSnapshot(metadata.getSequenceId(), true);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to recover order books from snapshot", e);
            return new RecoveredSnapshot(0L, false);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        ioExecutor.shutdown();
    }
    
    public static class RecoveredSnapshot {
        private final long sequenceId;
        private final boolean valid;
        public RecoveredSnapshot(long sequenceId, boolean valid) { this.sequenceId = sequenceId; this.valid = valid; }
        public long getSequenceId() { return sequenceId; }
        public boolean isValid() { return valid; }
    }
}
