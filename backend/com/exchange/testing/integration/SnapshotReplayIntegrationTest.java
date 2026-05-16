package com.exchange.testing.integration;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotReplayIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Full process restarts recover deterministically from snapshot and WAL")
    void simulatesFullRestartRecovery() throws Exception {
        Path walDir = tempDir.resolve("wal");
        Path snapshotDir = tempDir.resolve("snapshots");
        byte[] runtimeHash;
        long runtimeTrades;

        try (TestExchangeEngine live = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .build()) {
            for (int i = 0; i < 1_500; i++) {
                live.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.08d, 49_950.0d + (i % 40), "sr-" + i);
            }
            live.createSnapshot();
            live.waitForSnapshotCompletion(5, TimeUnit.SECONDS);
            for (int i = 0; i < 1_500; i++) {
                live.submitLimitOrder("ETH-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.12d, 3_000.0d + (i % 20), "sr2-" + i);
            }
            runtimeHash = live.captureStateHash();
            runtimeTrades = live.getTradeCount();
        }

        try (TestExchangeEngine recovered = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .build()) {
            recovered.recoverPreferSnapshotThenWal();
            assertArrayEquals(runtimeHash, recovered.captureStateHash());
            assertEquals(runtimeTrades, recovered.getTradeCount());
        }
    }
}
