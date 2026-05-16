package com.exchange.testing.chaos;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PartialSnapshotCorruptionTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Truncated snapshot files are rejected and recovery falls back to the WAL")
    void truncatedSnapshotsDoNotBreakRecovery() throws Exception {
        Path walDir = tempDir.resolve("wal");
        Path snapshotDir = tempDir.resolve("snapshots");
        byte[] runtimeHash;

        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .build()) {
            for (int i = 0; i < 900; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.03d, 50_000.0d + (i % 30), "snap-" + i);
            }
            engine.createSnapshot();
            engine.waitForSnapshotCompletion(5, TimeUnit.SECONDS);
            for (int i = 0; i < 300; i++) {
                engine.submitLimitOrder("ETH-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.05d, 3_000.0d + (i % 15), "late-" + i);
            }
            runtimeHash = engine.captureStateHash();
            engine.truncateActiveSnapshot(32);
        }

        try (TestExchangeEngine recovery = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .build()) {
            assertFalse(recovery.replayFromSnapshot());
            recovery.replayWalFromBeginning();
            assertArrayEquals(runtimeHash, recovery.captureStateHash());
        }
    }
}
