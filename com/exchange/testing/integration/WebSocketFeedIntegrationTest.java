package com.exchange.testing.integration;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.MarketDataReconstructor;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketFeedIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Incremental market data deltas reconstruct the same top-of-book state")
    void incrementalMarketDataStreamingRemainsConsistent() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            MarketDataReconstructor reconstructor = new MarketDataReconstructor();

            for (int i = 0; i < 300; i++) {
                long orderId = engine.submitLimitOrder("BTC-USD", OrderSide.BUY, 0.10d, 49_900.0d - (i % 20), "ws-" + i);
                if (i % 11 == 0) {
                    engine.cancelOrder(orderId, "ws-" + i);
                }
            }
            for (int i = 0; i < 150; i++) {
                engine.submitLimitOrder("BTC-USD", OrderSide.SELL, 0.10d, 50_100.0d + (i % 20), "ws-sell-" + i);
            }

            engine.getMarketDataDeltas().forEach(reconstructor::apply);
            TestExchangeEngine.BookDepthSnapshot expected = engine.getDepth("BTC-USD", 10);
            TestExchangeEngine.BookDepthSnapshot actual = reconstructor.snapshot("BTC-USD", 10);
            assertEquals(expected.bids().stream().map(level -> level.price() + ":" + level.quantity()).toList(),
                    actual.bids().stream().map(level -> level.price() + ":" + level.quantity()).toList());
            assertEquals(expected.asks().stream().map(level -> level.price() + ":" + level.quantity()).toList(),
                    actual.asks().stream().map(level -> level.price() + ":" + level.quantity()).toList());
        }
    }
}
