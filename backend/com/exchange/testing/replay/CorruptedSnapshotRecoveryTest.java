package com.exchange.testing.replay;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorruptedSnapshotRecoveryTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Corrupted snapshots are rejected and full WAL replay restores the runtime state")
    void corruptedSnapshotFallsBackToWalReplay() throws Exception {
        Path walDir = tempDir.resolve("wal");
        Path snapshotDir = tempDir.resolve("snapshots");
        byte[] runtimeHash;

        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .build()) {
            for (int i = 0; i < 600; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.15d, 50_050.0d + (i % 35), "c-" + i);
            }
            engine.createSnapshot();
            engine.waitForSnapshotCompletion(5, TimeUnit.SECONDS);
            for (int i = 0; i < 400; i++) {
                engine.submitLimitOrder("ETH-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.30d, 3_000.0d + (i % 18), "e-" + i);
            }
            runtimeHash = engine.captureStateHash();
            engine.corruptActiveSnapshot();
        }

        try (TestExchangeEngine recovery = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .build()) {
            assertFalse(recovery.replayFromSnapshot());
            recovery.replayWalFromBeginning();
            assertArrayEquals(runtimeHash, recovery.captureStateHash());
            assertTrue(recovery.getGlobalSequenceId() > 0);
        }
    }
}
