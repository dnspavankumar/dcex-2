package com.exchange.testing.latency;

import com.exchange.testing.support.BenchmarkReportWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OffHeapMemoryBenchmark {
    @Test
    @DisplayName("Direct buffer benchmark captures off-heap write and scan characteristics")
    void benchmarksDirectBufferAccess() throws Exception {
        int iterations = 1_000_000;
        ByteBuffer direct = ByteBuffer.allocateDirect(iterations * Long.BYTES);

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            direct.putLong(i * Long.BYTES, i);
        }
        long writeNs = System.nanoTime() - start;

        long checksum = 0L;
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            checksum += direct.getLong(i * Long.BYTES);
        }
        long readNs = System.nanoTime() - start;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("iterations", iterations);
        metrics.put("writeNs", writeNs);
        metrics.put("readNs", readNs);
        metrics.put("checksum", checksum);
        BenchmarkReportWriter.write("latency", "off-heap-memory", metrics);

        assertTrue(writeNs > 0L);
        assertTrue(readNs > 0L);
    }
}
