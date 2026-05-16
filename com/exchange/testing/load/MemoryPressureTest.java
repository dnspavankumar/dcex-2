package com.exchange.testing.load;

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

class MemoryPressureTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Hot-path memory growth remains bounded during sustained order flow")
    void memoryGrowthRemainsBounded() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long before = runtime.totalMemory() - runtime.freeMemory();

        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            for (int i = 0; i < 15_000; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.02d, 50_000.0d + (i % 20), "mem-" + (i % 80));
            }
        }

        System.gc();
        long after = runtime.totalMemory() - runtime.freeMemory();
        long delta = Math.max(0L, after - before);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("heapBeforeBytes", before);
        metrics.put("heapAfterBytes", after);
        metrics.put("heapDeltaBytes", delta);
        BenchmarkReportWriter.write("load", "memory-pressure", metrics);

        assertTrue(delta < 256L * 1024L * 1024L);
    }
}
