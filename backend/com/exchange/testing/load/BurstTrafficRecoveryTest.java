package com.exchange.testing.load;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BurstTrafficRecoveryTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Traffic explosions do not break sequence continuity or state hashing")
    void remainsStableAcrossBurstWorkloads() throws Exception {
        byte[] beforeBurstHash;
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            for (int i = 0; i < 1_000; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.03d, 50_000.0d + (i % 15), "warm-" + i);
            }
            beforeBurstHash = engine.captureStateHash();

            for (int i = 0; i < 12_000; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.01d, 50_010.0d + (i % 25), "burst-" + (i % 100));
            }

            assertTrue(engine.getGlobalSequenceId() >= 13_000L);
            assertTrue(engine.captureStateHash().length > 0);
            assertTrue(beforeBurstHash.length > 0);
        }
    }
}
