package com.exchange.testing.chaos;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadInterruptionChaosTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Interrupted replay workers can be restarted without corrupting recovery")
    void interruptedReplayCanResumeCleanly() throws Exception {
        Path walDir = tempDir.resolve("wal");
        byte[] referenceHash;

        try (TestExchangeEngine source = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            for (int i = 0; i < 5_000; i++) {
                source.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.01d, 50_000.0d + (i % 40), "interrupt-" + i);
            }
            referenceHash = source.captureStateHash();
        }

        TestExchangeEngine interruptedReplay = TestExchangeEngine.builder()
                .walDirectory(walDir)
                .enableDeterministicMode(false)
                .replayBatchSize(1)
                .build();
        Thread replayThread = new Thread(() -> {
            try {
                interruptedReplay.replayWalFromBeginning();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        replayThread.start();
        replayThread.interrupt();
        replayThread.join(2_000L);
        interruptedReplay.shutdown();

        try (TestExchangeEngine resumedReplay = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            resumedReplay.replayWalFromBeginning();
            assertArrayEquals(referenceHash, resumedReplay.captureStateHash());
            assertTrue(resumedReplay.getGlobalSequenceId() >= 5_000L);
        }
    }
}
