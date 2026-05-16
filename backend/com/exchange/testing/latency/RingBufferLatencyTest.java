package com.exchange.testing.latency;

import com.exchange.common.concurrency.RingBuffer;
import com.exchange.testing.support.BenchmarkReportWriter;
import com.exchange.testing.support.LatencyHistogram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RingBufferLatencyTest {
    @Test
    @DisplayName("Producer-consumer ring buffer latency remains measurable under contention")
    void measuresQueueLatency() throws Exception {
        RingBuffer<Event> ringBuffer = new RingBuffer<>(2_048, Event::new);
        LatencyHistogram histogram = new LatencyHistogram();
        int iterations = 50_000;

        Thread consumer = new Thread(() -> {
            long readSequence = 0L;
            while (readSequence < iterations) {
                if (ringBuffer.hasAvailable(readSequence)) {
                    Event event = ringBuffer.get(readSequence);
                    histogram.record(System.nanoTime() - event.timestampNs);
                    readSequence++;
                } else {
                    Thread.onSpinWait();
                }
            }
        });
        consumer.start();

        for (int i = 0; i < iterations; i++) {
            long sequence = ringBuffer.nextWrite();
            Event event = ringBuffer.get(sequence);
            event.timestampNs = System.nanoTime();
            ringBuffer.publish(sequence);
        }

        consumer.join(5_000L);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("samples", histogram.size());
        metrics.put("p50Ns", histogram.percentile(0.50d));
        metrics.put("p99Ns", histogram.percentile(0.99d));
        BenchmarkReportWriter.write("latency", "ring-buffer", metrics);

        assertTrue(histogram.size() == iterations);
        assertTrue(histogram.percentile(0.99d) >= histogram.percentile(0.50d));
    }

    private static final class Event {
        long timestampNs;
    }
}
