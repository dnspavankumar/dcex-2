package com.exchange.testing.chaos;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventStreamerFailureTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("WebSocket or event-streamer failures never stop the matching loop")
    void eventStreamingFailuresRemainDecoupled() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal"))
                .websocketFailureMode(true)
                .build()) {
            engine.submitLimitOrder("BTC-USD", OrderSide.BUY, 1.0d, 50_000.0d, "maker");
            engine.submitLimitOrder("BTC-USD", OrderSide.SELL, 1.0d, 49_999.0d, "taker");

            assertEquals(1L, engine.getTradeCount());
            assertTrue(engine.getGlobalSequenceId() >= 2L);
            assertTrue(engine.getMarketDataDeltas().size() >= 2);
        }
    }
}
