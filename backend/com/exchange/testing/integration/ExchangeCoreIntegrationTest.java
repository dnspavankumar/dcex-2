package com.exchange.testing.integration;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeCoreIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Order submission traverses risk, sequencing, WAL, matching, and streaming stages")
    void validatesCompleteOrderLifecycle() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            long restingBuy = engine.submitLimitOrder("BTC-USD", OrderSide.BUY, 1.00d, 50_000.0d, "buyer");
            long takerSell = engine.submitLimitOrder("BTC-USD", OrderSide.SELL, 1.00d, 49_999.0d, "seller");

            Awaitility.await().atMost(Duration.ofSeconds(2))
                    .until(() -> engine.getPersistenceProcessed() > 0L && engine.getWebsocketProcessed() > 0L);

            List<String> buyTrace = engine.getLifecycleTrace(restingBuy);
            List<String> sellTrace = engine.getLifecycleTrace(takerSell);

            assertTrue(buyTrace.containsAll(List.of("risk", "sequence", "wal", "match")));
            assertTrue(sellTrace.containsAll(List.of("risk", "sequence", "wal", "match")));
            assertEquals(1L, engine.getTradeCount());
            assertTrue(engine.getMarketDataDeltas().size() >= 2);
        }
    }
}
