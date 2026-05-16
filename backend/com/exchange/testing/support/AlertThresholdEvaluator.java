package com.exchange.testing.support;

public final class AlertThresholdEvaluator {
    public boolean shouldAlertOnPersistenceLag(long lagEvents, long threshold) {
        return lagEvents >= threshold;
    }

    public boolean shouldAlertOnReplayFailure(boolean snapshotValid) {
        return !snapshotValid;
    }

    public boolean shouldAlertOnLatencySpike(long p99LatencyNs, long thresholdNs) {
        return p99LatencyNs >= thresholdNs;
    }
}
