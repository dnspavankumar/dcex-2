package com.exchange.testing.metrics;

import com.exchange.testing.support.AlertThresholdEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertThresholdTest {
    @Test
    @DisplayName("Operational alert thresholds fire for lag, replay failure, and latency spikes")
    void validatesAlertThresholds() {
        AlertThresholdEvaluator evaluator = new AlertThresholdEvaluator();

        assertTrue(evaluator.shouldAlertOnPersistenceLag(10_000L, 5_000L));
        assertTrue(evaluator.shouldAlertOnReplayFailure(false));
        assertTrue(evaluator.shouldAlertOnLatencySpike(2_000_000L, 1_000_000L));
        assertFalse(evaluator.shouldAlertOnPersistenceLag(100L, 5_000L));
    }
}
