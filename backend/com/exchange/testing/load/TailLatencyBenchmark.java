package com.exchange.testing.load;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.BenchmarkReportWriter;
import com.exchange.testing.support.LatencyHistogram;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TailLatencyBenchmark {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Tail latency benchmark records p50 through p99.99 distributions")
    void capturesLatencyPercentiles() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            for (int i = 0; i < 10_000; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.02d, 50_000.0d + (i % 30), "lat-" + (i % 32));
            }

            LatencyHistogram histogram = engine.submitLatencyHistogram();
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("samples", histogram.size());
            metrics.put("p50Ns", histogram.percentile(0.50d));
            metrics.put("p99Ns", histogram.percentile(0.99d));
            metrics.put("p999Ns", histogram.percentile(0.999d));
            metrics.put("p9999Ns", histogram.percentile(0.9999d));
            BenchmarkReportWriter.write("load", "tail-latency", metrics);

            assertTrue(histogram.percentile(0.50d) <= histogram.percentile(0.99d));
            assertTrue(histogram.percentile(0.99d) <= histogram.percentile(0.999d));
            assertTrue(histogram.percentile(0.999d) <= histogram.percentile(0.9999d));
        }
    }
}
