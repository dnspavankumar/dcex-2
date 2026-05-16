package com.exchange.testing.latency;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.BenchmarkReportWriter;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotGenerationLatencyTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Asynchronous snapshot generation does not stall order submission")
    void snapshotGenerationStaysOffHotPath() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal"))
                .snapshotDirectory(tempDir.resolve("snapshots"))
                .build()) {
            for (int i = 0; i < 2_000; i++) {
                engine.submitLimitOrder("BTC-USD", OrderSide.BUY, 0.02d, 50_000.0d + (i % 10), "pre-" + i);
            }

            long baselineP99 = engine.submitLatencyHistogram().percentile(0.99d);
            engine.createSnapshot();
            for (int i = 0; i < 2_000; i++) {
                engine.submitLimitOrder("BTC-USD", OrderSide.SELL, 0.02d, 50_010.0d + (i % 10), "post-" + i);
            }
            engine.waitForSnapshotCompletion(5, TimeUnit.SECONDS);

            long stressedP99 = engine.submitLatencyHistogram().percentile(0.99d);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("baselineP99Ns", baselineP99);
            metrics.put("stressedP99Ns", stressedP99);
            BenchmarkReportWriter.write("latency", "snapshot-generation", metrics);

            assertTrue(stressedP99 <= Math.max(5_000_000L, baselineP99 * 10L));
        }
    }
}
