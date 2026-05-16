package com.exchange.testing.fuzz;

import com.exchange.matching.models.Order;
import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.SyntheticOrderGenerator;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookFuzzTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Randomized order flow preserves non-crossed books and positive open quantities")
    void validatesOrderBookInvariants() throws Exception {
        SyntheticOrderGenerator generator = new SyntheticOrderGenerator();
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            for (var instruction : generator.randomizedFlow("BTC-USD", 3_000, 42L)) {
                engine.submitLimitOrder(instruction.symbol(), instruction.side(), instruction.quantity(), instruction.price(), instruction.user());
            }

            TestExchangeEngine.BookDepthSnapshot depth = engine.getDepth("BTC-USD", 1);
            if (!depth.bids().isEmpty() && !depth.asks().isEmpty()) {
                assertTrue(depth.bids().get(0).price().compareTo(depth.asks().get(0).price()) <= 0);
            }

            for (Order order : engine.getAllPendingOrders()) {
                assertTrue(order.getRemainingQuantity().signum() > 0);
            }
        }
    }
}
