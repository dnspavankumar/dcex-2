package com.exchange.testing.replay;

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

class ReplayPerformanceBenchmark {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Replay benchmark emits deterministic throughput and latency metrics")
    void measuresReplayThroughputAndLatency() throws Exception {
        Path walDir = tempDir.resolve("wal");
        int orderCount = 20_000;

        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            for (int i = 0; i < orderCount; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.05d, 49_500.0d + (i % 50), "perf-" + (i % 100));
            }
        }

        long started = System.nanoTime();
        try (TestExchangeEngine replay = TestExchangeEngine.builder().walDirectory(walDir).replayBatchSize(512).build()) {
            replay.replayWalFromBeginning();
            long elapsedNs = System.nanoTime() - started;
            double elapsedSeconds = elapsedNs / 1_000_000_000.0d;
            double throughput = orderCount / elapsedSeconds;

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("orders", orderCount);
            metrics.put("elapsedNs", elapsedNs);
            metrics.put("elapsedMs", elapsedNs / 1_000_000.0d);
            metrics.put("throughputOrdersPerSecond", throughput);
            metrics.put("finalSequence", replay.getGlobalSequenceId());
            BenchmarkReportWriter.write("replay", "replay-performance", metrics);

            assertTrue(throughput > 0.0d);
            assertTrue(replay.getGlobalSequenceId() >= orderCount);
        }
    }
}
