package com.exchange.testing.replay;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotRecoveryTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Crash during snapshot rotation preserves the previous committed snapshot")
    void tempSnapshotIsDiscardedAndRecoveryUsesAtomicSnapshot() throws Exception {
        Path walDir = tempDir.resolve("wal");
        Path snapshotDir = tempDir.resolve("snapshots");
        byte[] stableHash;

        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .build()) {
            for (int i = 0; i < 500; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.10d, 50_000.0d + (i % 25), "snap-" + i);
            }
            stableHash = engine.captureStateHash();
            engine.createSnapshot();
            assertTrue(engine.waitForSnapshotCompletion(5, TimeUnit.SECONDS));

            engine.createOrphanedTempSnapshot("interrupted-rotation".getBytes(StandardCharsets.UTF_8));
            assertTrue(Files.exists(engine.getTempSnapshotPath()));
        }

        try (TestExchangeEngine recovery = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .build()) {
            assertTrue(recovery.replayFromSnapshot());
            assertFalse(Files.exists(recovery.getTempSnapshotPath()));
            assertArrayEquals(stableHash, recovery.captureStateHash());
        }
    }
}
