package com.exchange.testing.fuzz;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyRaceTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Parallel submission and cancellation preserves replayable state")
    void parallelAccessDoesNotLoseState() throws Exception {
        Path walDir = tempDir.resolve("wal");
        byte[] runtimeHash;

        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            List<Long> orderIds = java.util.Collections.synchronizedList(new ArrayList<>());
            int threads = 6;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 400; i++) {
                            OrderSide side = threadId % 2 == 0 ? OrderSide.BUY : OrderSide.SELL;
                            double price = side == OrderSide.BUY ? 49_800.0d - (i % 20) : 50_200.0d + (i % 20);
                            long orderId = engine.submitLimitOrder("BTC-USD", side, 0.03d, price, "race-" + threadId);
                            orderIds.add(orderId);
                            if (i % 5 == 0) {
                                engine.cancelOrder(orderId, "race-" + threadId);
                            }
                        }
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            start.countDown();
            done.await();
            runtimeHash = engine.captureStateHash();
            assertTrue(engine.getGlobalSequenceId() > 0L);
        }

        try (TestExchangeEngine replay = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            replay.replayWalFromBeginning();
            assertArrayEquals(runtimeHash, replay.captureStateHash());
            assertTrue(replay.getGlobalSequenceId() > 0L);
        }
    }
}
