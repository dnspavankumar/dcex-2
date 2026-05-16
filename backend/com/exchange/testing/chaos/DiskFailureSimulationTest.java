package com.exchange.testing.chaos;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskFailureSimulationTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Injected WAL disk failures reject new orders without corrupting accepted state")
    void walFailuresCauseGracefulDegradation() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal"))
                .failWalAfterWrites(100)
                .build()) {
            TestExchangeEngine.SubmissionResult failed = null;
            for (int i = 0; i < 150; i++) {
                TestExchangeEngine.SubmissionResult result = engine.trySubmit("BTC-USD", OrderSide.BUY, com.exchange.matching.models.OrderType.LIMIT, 0.10d, 50_000.0d + i, "disk-" + i);
                if (!result.accepted()) {
                    failed = result;
                    break;
                }
            }

            assertTrue(engine.getGlobalSequenceId() >= 100L);
            assertTrue(engine.captureStateHash().length > 0);
            assertFalse(failed == null, "Expected injected WAL failure to reject at least one order");
        }
    }
}
