package com.exchange.testing.metrics;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.BenchmarkReportWriter;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryOverheadTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Instrumentation overhead remains bounded relative to a no-metrics baseline")
    void measuresTelemetryHotPathCost() throws Exception {
        long withTelemetryNs;
        try (TestExchangeEngine instrumented = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal-a"))
                .recordSubmitLatencies(true)
                .build()) {
            long start = System.nanoTime();
            for (int i = 0; i < 5_000; i++) {
                instrumented.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.01d, 50_000.0d + (i % 12), "tele-" + i);
            }
            withTelemetryNs = System.nanoTime() - start;
        }

        long withoutTelemetryNs;
        try (TestExchangeEngine baseline = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal-b"))
                .recordSubmitLatencies(false)
                .build()) {
            long start = System.nanoTime();
            for (int i = 0; i < 5_000; i++) {
                baseline.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.01d, 50_000.0d + (i % 12), "base-" + i);
            }
            withoutTelemetryNs = System.nanoTime() - start;
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("withTelemetryNs", withTelemetryNs);
        metrics.put("withoutTelemetryNs", withoutTelemetryNs);
        metrics.put("overheadRatio", withoutTelemetryNs == 0L ? 0.0d : (double) withTelemetryNs / withoutTelemetryNs);
        BenchmarkReportWriter.write("metrics", "telemetry-overhead", metrics);

        assertTrue(withTelemetryNs > 0L);
        assertTrue(withoutTelemetryNs > 0L);
        assertTrue(withTelemetryNs <= withoutTelemetryNs * 20L);
    }
}
