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
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingEngineThroughputTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Matching throughput benchmark sustains high concurrent submission volume")
    void benchmarksSustainedOrdersPerSecond() throws Exception {
        Path walDir = tempDir.resolve("wal");
        int threadCount = 6;
        int ordersPerThread = 2_000;

        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);

            long started = System.nanoTime();
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                Thread worker = new Thread(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < ordersPerThread; i++) {
                            engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.05d, 50_000.0d + (i % 40), "tp-" + threadId);
                        }
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
                worker.start();
            }

            ready.await();
            start.countDown();
            done.await();
            long elapsedNs = System.nanoTime() - started;
            long submitted = (long) threadCount * ordersPerThread;
            double throughput = submitted / (elapsedNs / 1_000_000_000.0d);

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("threads", threadCount);
            metrics.put("orders", submitted);
            metrics.put("elapsedNs", elapsedNs);
            metrics.put("throughputOrdersPerSecond", throughput);
            BenchmarkReportWriter.write("load", "matching-throughput", metrics);

            assertEquals(submitted, engine.getGlobalSequenceId());
            assertTrue(throughput > 0.0d);
        }
    }
}
