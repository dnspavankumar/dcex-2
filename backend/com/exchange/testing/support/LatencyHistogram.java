package com.exchange.testing.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LatencyHistogram {
    private final List<Long> samplesNs = Collections.synchronizedList(new ArrayList<>());

    public void record(long sampleNs) {
        samplesNs.add(sampleNs);
    }

    public int size() {
        return samplesNs.size();
    }

    public long percentile(double percentile) {
        if (samplesNs.isEmpty()) {
            return 0L;
        }

        List<Long> copy;
        synchronized (samplesNs) {
            copy = new ArrayList<>(samplesNs);
        }
        copy.sort(Long::compareTo);

        int index = (int) Math.ceil(percentile * copy.size()) - 1;
        index = Math.max(0, Math.min(copy.size() - 1, index));
        return copy.get(index);
    }

    public long min() {
        return percentile(0.0d);
    }

    public long max() {
        return percentile(1.0d);
    }

    public double average() {
        if (samplesNs.isEmpty()) {
            return 0.0d;
        }

        long total = 0L;
        synchronized (samplesNs) {
            for (long value : samplesNs) {
                total += value;
            }
        }
        return total / (double) samplesNs.size();
    }
}
