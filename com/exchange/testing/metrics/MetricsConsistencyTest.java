package com.exchange.testing.metrics;

import com.exchange.observability.metrics.LatencyObservabilityFramework;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsConsistencyTest {
    @Test
    @DisplayName("Latency and throughput counters remain mathematically consistent")
    void metricsStayAccurate() {
        LatencyObservabilityFramework metrics = new LatencyObservabilityFramework();
        metrics.recordQueueLatency(100L);
        metrics.recordQueueLatency(300L);
        metrics.recordMatchingLatency(200L);
        metrics.recordMatchingLatency(600L);
        metrics.recordWalFsyncLatency(500L);
        metrics.recordPersistenceQueueDepth(12);
        metrics.recordPersistenceLag(7L);
        metrics.recordSnapshotMetrics(25L, 1_024L);
        metrics.recordRecoveryMetrics(40L, 60L, true);

        assertEquals(2L, metrics.getEventCount());
        assertEquals(400L, metrics.getTotalQueueLatencyNs());
        assertEquals(300L, metrics.getMaxQueueLatencyNs());
        assertEquals(800L, metrics.getTotalMatchingLatencyNs());
        assertEquals(600L, metrics.getMaxMatchingLatencyNs());
        assertEquals(200.0d, metrics.getAverageQueueLatencyNs());
        assertEquals(400.0d, metrics.getAverageMatchingLatencyNs());
        assertEquals(500L, metrics.getTotalFsyncLatencyNs());
        assertEquals(12, metrics.getPersistenceQueueDepth());
        assertEquals(7L, metrics.getPersistenceLagEvents());
        assertEquals(25L, metrics.getLastSnapshotGenerationTimeMs());
        assertEquals(1_024L, metrics.getLastSnapshotSizeBytes());
        assertEquals(40L, metrics.getWalReplayDurationMs());
        assertEquals(60L, metrics.getRecoveryCompletionLatencyMs());
    }
}
