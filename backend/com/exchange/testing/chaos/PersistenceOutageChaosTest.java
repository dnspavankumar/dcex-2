package com.exchange.testing.chaos;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceOutageChaosTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Persistence outages are absorbed asynchronously without stopping matching")
    void neonOutageDoesNotBlockCoreFlow() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal"))
                .persistenceFailureMode(true)
                .persistenceQueueCapacity(32)
                .build()) {
            engine.pausePersistenceConsumer();
            for (int i = 0; i < 2_000; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.02d, 50_000.0d + (i % 30), "out-" + i);
            }

            assertTrue(engine.getGlobalSequenceId() >= 2_000L);
            assertTrue(engine.getPersistenceBacklog() > 0L || engine.getPersistenceDrops() > 0L);
        }
    }
}
