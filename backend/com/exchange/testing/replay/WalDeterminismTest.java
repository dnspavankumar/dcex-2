package com.exchange.testing.replay;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WalDeterminismTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Identical WAL streams rebuild the same state independent of replay pacing")
    void identicalWalAlwaysProducesSameState() throws Exception {
        Path walDir = tempDir.resolve("wal");

        byte[] referenceHash;
        try (TestExchangeEngine generator = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            for (int i = 0; i < 2_000; i++) {
                generator.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.05d, 49_900.0d + (i % 100), "user-" + (i % 40));
            }
            referenceHash = generator.captureStateHash();
        }

        for (int batchSize : new int[]{1, 17, 257}) {
            try (TestExchangeEngine replay = TestExchangeEngine.builder()
                    .walDirectory(walDir)
                    .replayBatchSize(batchSize)
                    .enableDeterministicMode(batchSize == 1)
                    .build()) {
                replay.replayWalFromBeginning();
                assertArrayEquals(referenceHash, replay.captureStateHash());
                assertEquals(2_000L, replay.getGlobalSequenceId());
            }
        }
    }
}
