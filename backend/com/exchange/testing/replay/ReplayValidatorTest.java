package com.exchange.testing.replay;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplayValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Snapshot plus WAL replay rebuilds the same runtime state hash")
    void reconstructsRuntimeStateFromSnapshotAndWal() throws Exception {
        Path walDir = tempDir.resolve("wal");
        Path snapshotDir = tempDir.resolve("snapshots");

        byte[] runtimeHash;
        long runtimeSequence;
        long runtimeTrades;
        Map<Long, Map<String, java.math.BigDecimal>> runtimeBalances;
        TestExchangeEngine.BookDepthSnapshot runtimeDepth;

        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .enableDeterministicMode(true)
                .build()) {
            for (int i = 0; i < 400; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.25d, 50_000.0d + (i % 30), "seed-" + (i % 20));
            }
            engine.createSnapshot();
            engine.waitForSnapshotCompletion(5, TimeUnit.SECONDS);

            for (int i = 0; i < 800; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.10d + (i % 5) * 0.01d, 50_010.0d + (i % 60), "flow-" + (i % 50));
            }
            for (int i = 0; i < 120; i++) {
                long orderId = engine.submitLimitOrder("ETH-USD", OrderSide.BUY, 0.50d, 3_000.0d + i, "eth-" + i);
                if (i % 3 == 0) {
                    engine.cancelOrder(orderId, "eth-" + i);
                }
            }

            runtimeHash = engine.captureStateHash();
            runtimeSequence = engine.getGlobalSequenceId();
            runtimeTrades = engine.getTradeCount();
            runtimeBalances = engine.getAllAccountBalances();
            runtimeDepth = engine.getDepth("BTC-USD", 10);
        }

        try (TestExchangeEngine replay = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .snapshotDirectory(snapshotDir)
                .enableDeterministicMode(true)
                .build()) {
            replay.recoverPreferSnapshotThenWal();

            assertArrayEquals(runtimeHash, replay.captureStateHash());
            assertEquals(runtimeSequence, replay.getGlobalSequenceId());
            assertEquals(runtimeTrades, replay.getTradeCount());
            assertEquals(runtimeBalances, replay.getAllAccountBalances());
            assertEquals(runtimeDepth, replay.getDepth("BTC-USD", 10));
        }
    }
}
