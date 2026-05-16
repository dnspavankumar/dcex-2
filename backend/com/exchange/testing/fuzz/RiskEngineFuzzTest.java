package com.exchange.testing.fuzz;

import com.exchange.matching.models.OrderSide;
import com.exchange.matching.models.OrderType;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskEngineFuzzTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Risk validation rejects malformed and underfunded orders deterministically")
    void malformedOrdersAreRejected() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal"))
                .autoSeedBalances(false)
                .build()) {
            engine.seedBalance("funded", "USD", BigDecimal.valueOf(100));

            assertThrows(IllegalArgumentException.class, () -> engine.submitLimitOrder("BTC-USD", OrderSide.BUY, -1.0d, 50_000.0d, "funded"));
            assertThrows(IllegalArgumentException.class, () -> engine.submitLimitOrder("BTC-USD", OrderSide.BUY, 1.0d, Double.NaN, "funded"));

            TestExchangeEngine.SubmissionResult rejected = engine.trySubmit("BTC-USD", OrderSide.BUY, OrderType.LIMIT, 1.0d, 50_000.0d, "funded");
            assertFalse(rejected.accepted());
        }
    }
}
