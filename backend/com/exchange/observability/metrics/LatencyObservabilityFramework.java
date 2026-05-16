package com.exchange.observability.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nanosecond-precision observability framework.
 * In a real deployment, this would wrap HdrHistogram for lock-free percentile aggregations 
 * (p50, p90, p99, p99.9, max).
 */
public class LatencyObservabilityFramework {

    // Hot-Path Metrics
    private final AtomicLong totalQueueLatencyNs = new AtomicLong(0);
    private final AtomicLong maxQueueLatencyNs = new AtomicLong(0);
    private final AtomicLong totalMatchingLatencyNs = new AtomicLong(0);
    private final AtomicLong maxMatchingLatencyNs = new AtomicLong(0);
    private final AtomicLong totalFsyncLatencyNs = new AtomicLong(0);
    private final AtomicLong eventCount = new AtomicLong(0);

    // Persistence & Snapshot Metrics
    private final AtomicInteger persistenceQueueDepth = new AtomicInteger(0);
    private final AtomicLong persistenceLagEvents = new AtomicLong(0);
    private final AtomicLong lastSnapshotGenerationTimeMs = new AtomicLong(0);
    private final AtomicLong lastSnapshotSizeBytes = new AtomicLong(0);

    // Recovery Metrics
    private final AtomicLong walReplayDurationMs = new AtomicLong(0);
    private final AtomicLong recoveryCompletionLatencyMs = new AtomicLong(0);
    private final AtomicBoolean lastSnapshotValidationSuccess = new AtomicBoolean(false);

    public void recordQueueLatency(long latencyNs) {
        totalQueueLatencyNs.addAndGet(latencyNs);
        eventCount.incrementAndGet();
        updateMax(maxQueueLatencyNs, latencyNs);
    }

    public void recordMatchingLatency(long latencyNs) {
        totalMatchingLatencyNs.addAndGet(latencyNs);
        updateMax(maxMatchingLatencyNs, latencyNs);
    }

    public void recordWalFsyncLatency(long latencyNs) {
        totalFsyncLatencyNs.addAndGet(latencyNs);
    }

    public void recordPersistenceQueueDepth(int depth) {
        persistenceQueueDepth.set(depth);
    }

    public void recordPersistenceLag(long lag) {
        persistenceLagEvents.set(lag);
    }

    public void recordSnapshotMetrics(long durationMs, long sizeBytes) {
        lastSnapshotGenerationTimeMs.set(durationMs);
        lastSnapshotSizeBytes.set(sizeBytes);
    }

    public void recordRecoveryMetrics(long replayDurationMs, long totalRecoveryMs, boolean snapshotValid) {
        walReplayDurationMs.set(replayDurationMs);
        recoveryCompletionLatencyMs.set(totalRecoveryMs);
        lastSnapshotValidationSuccess.set(snapshotValid);
    }

    public long getTotalQueueLatencyNs() {
        return totalQueueLatencyNs.get();
    }

    public long getMaxQueueLatencyNs() {
        return maxQueueLatencyNs.get();
    }

    public long getTotalMatchingLatencyNs() {
        return totalMatchingLatencyNs.get();
    }

    public long getMaxMatchingLatencyNs() {
        return maxMatchingLatencyNs.get();
    }

    public long getTotalFsyncLatencyNs() {
        return totalFsyncLatencyNs.get();
    }

    public long getEventCount() {
        return eventCount.get();
    }

    public int getPersistenceQueueDepth() {
        return persistenceQueueDepth.get();
    }

    public long getPersistenceLagEvents() {
        return persistenceLagEvents.get();
    }

    public long getLastSnapshotGenerationTimeMs() {
        return lastSnapshotGenerationTimeMs.get();
    }

    public long getLastSnapshotSizeBytes() {
        return lastSnapshotSizeBytes.get();
    }

    public long getWalReplayDurationMs() {
        return walReplayDurationMs.get();
    }

    public long getRecoveryCompletionLatencyMs() {
        return recoveryCompletionLatencyMs.get();
    }

    public boolean wasLastSnapshotValidationSuccessful() {
        return lastSnapshotValidationSuccess.get();
    }

    public double getAverageQueueLatencyNs() {
        long count = eventCount.get();
        return count == 0 ? 0.0d : (double) totalQueueLatencyNs.get() / count;
    }

    public double getAverageMatchingLatencyNs() {
        long count = eventCount.get();
        return count == 0 ? 0.0d : (double) totalMatchingLatencyNs.get() / count;
    }

    private void updateMax(AtomicLong maxMetric, long newVal) {
        long current;
        do {
            current = maxMetric.get();
            if (newVal <= current) break;
        } while (!maxMetric.compareAndSet(current, newVal));
    }
    
    public void dumpMetrics() {
        long count = eventCount.get();
        if (count == 0) return;
        
        System.out.printf("[METRICS] Throughput: %d ev | Max Queue: %d ns | Max Match: %d ns | DB Queue: %d | Snapshot Time: %d ms | Snapshot Size: %d bytes%n",
                count, maxQueueLatencyNs.get(), maxMatchingLatencyNs.get(), 
                persistenceQueueDepth.get(), lastSnapshotGenerationTimeMs.get(), lastSnapshotSizeBytes.get());
    }
}
