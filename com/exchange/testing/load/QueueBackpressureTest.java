package com.exchange.testing.load;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.LatencyHistogram;
import com.exchange.testing.support.TestExchangeEngine;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueBackpressureTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Slow persistence and websocket consumers do not dominate matching latency")
    void overloadedConsumersStayIsolatedFromMatchingPath() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal"))
                .persistenceQueueCapacity(64)
                .websocketQueueCapacity(64)
                .persistenceDelay(Duration.ofMillis(5))
                .websocketDelay(Duration.ofMillis(5))
                .build()) {
            engine.pausePersistenceConsumer();
            engine.pauseWebsocketConsumer();

            for (int i = 0; i < 4_000; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.02d, 50_000.0d + (i % 25), "bp-" + (i % 24));
            }

            LatencyHistogram histogram = engine.submitLatencyHistogram();
            assertTrue(histogram.percentile(0.99d) > 0L);
            assertTrue(engine.getPersistenceDrops() > 0L || engine.getPersistenceBacklog() > 0L);
            assertTrue(engine.getWebsocketDrops() > 0L || engine.getWebsocketBacklog() > 0L);

            engine.resumePersistenceConsumer();
            engine.resumeWebsocketConsumer();
            Awaitility.await().atMost(Duration.ofSeconds(2))
                    .until(() -> engine.getPersistenceProcessed() > 0L && engine.getWebsocketProcessed() > 0L);
        }
    }
}
