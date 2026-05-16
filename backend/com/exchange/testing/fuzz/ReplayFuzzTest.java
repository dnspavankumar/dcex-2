package com.exchange.testing.fuzz;

import com.exchange.testing.support.SyntheticOrderGenerator;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ReplayFuzzTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Randomized WAL streams replay identically across timing profiles")
    void replayedFuzzWalIsDeterministic() throws Exception {
        Path walDir = tempDir.resolve("wal");
        SyntheticOrderGenerator generator = new SyntheticOrderGenerator();
        byte[] runtimeHash;

        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(walDir).build()) {
            for (var instruction : generator.randomizedFlow("BTC-USD", 4_000, 777L)) {
                engine.submitLimitOrder(instruction.symbol(), instruction.side(), instruction.quantity(), instruction.price(), instruction.user());
            }
            runtimeHash = engine.captureStateHash();
        }

        for (int batchSize : new int[]{1, 11, 97}) {
            try (TestExchangeEngine replay = TestExchangeEngine.builder()
                    .walDirectory(walDir)
                    .replayBatchSize(batchSize)
                    .enableDeterministicMode(batchSize == 1)
                    .build()) {
                replay.replayWalFromBeginning();
                assertArrayEquals(runtimeHash, replay.captureStateHash());
            }
        }
    }
}
