package com.exchange.testing.replay;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceIntegrityTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Global sequence IDs stay contiguous under concurrent submission")
    void sequenceIdsRemainMonotonicUnderConcurrency() throws Exception {
        Path walDir = tempDir.resolve("wal");
        Set<Long> sequences = ConcurrentHashMap.newKeySet();

        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            int threadCount = 8;
            int ordersPerThread = 250;
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                Thread worker = new Thread(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < ordersPerThread; i++) {
                            long orderId = engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.20d, 50_000.0d + i, "u-" + threadId);
                            sequences.add(engine.getOrderSequenceId(orderId));
                        }
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }, "sequence-worker-" + t);
                worker.start();
            }

            ready.await();
            start.countDown();
            done.await();

            assertEquals(threadCount * ordersPerThread, sequences.size());
            List<Long> ordered = new ArrayList<>(sequences);
            ordered.sort(Long::compareTo);
            for (int i = 1; i < ordered.size(); i++) {
                assertEquals(ordered.get(i - 1) + 1L, ordered.get(i));
            }

            try (TestExchangeEngine replay = TestExchangeEngine.builder().walDirectory(walDir).build()) {
                replay.replayWalFromBeginning();
                assertEquals(engine.getGlobalSequenceId(), replay.getGlobalSequenceId());
                assertTrue(sequences.stream().allMatch(replay::hasSequenceId));
            }
        }
    }
}
