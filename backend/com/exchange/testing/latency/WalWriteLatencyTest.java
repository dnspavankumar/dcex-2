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

import static org.junit.jupiter.api.Assertions.assertTrue;

class WalWriteLatencyTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("WAL append latency benchmark emits a deterministic percentile report")
    void benchmarksWalAppendLatency() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            for (int i = 0; i < 8_000; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.01d, 50_000.0d + (i % 10), "wal-" + i);
            }

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("samples", engine.submitLatencyHistogram().size());
            metrics.put("p50SubmitNs", engine.submitLatencyHistogram().percentile(0.50d));
            metrics.put("p99SubmitNs", engine.submitLatencyHistogram().percentile(0.99d));
            BenchmarkReportWriter.write("latency", "wal-write", metrics);

            assertTrue(engine.submitLatencyHistogram().percentile(0.99d) >= engine.submitLatencyHistogram().percentile(0.50d));
        }
    }
}
