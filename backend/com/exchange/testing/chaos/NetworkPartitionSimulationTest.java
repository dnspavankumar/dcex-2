package com.exchange.testing.chaos;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPartitionSimulationTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Disconnected downstream services create backlog without stalling sequencing")
    void downstreamPartitionsRemainIsolated() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal"))
                .persistenceDelay(Duration.ofMillis(50))
                .websocketDelay(Duration.ofMillis(50))
                .persistenceQueueCapacity(48)
                .websocketQueueCapacity(48)
                .build()) {
            engine.pausePersistenceConsumer();
            engine.pauseWebsocketConsumer();

            for (int i = 0; i < 3_000; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.02d, 50_000.0d + (i % 25), "net-" + i);
            }

            assertTrue(engine.getGlobalSequenceId() >= 3_000L);
            assertTrue(engine.getPersistenceBacklog() > 0L || engine.getPersistenceDrops() > 0L);
            assertTrue(engine.getWebsocketBacklog() > 0L || engine.getWebsocketDrops() > 0L);
        }
    }
}
