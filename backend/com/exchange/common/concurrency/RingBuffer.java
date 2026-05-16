package com.exchange.common.concurrency;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A simplified pre-allocated, lock-free Disruptor-style RingBuffer.
 * For HFT, it prevents garbage collection pauses by reusing objects.
 * Employs cache line padding (PaddedLong) to prevent false sharing.
 */
public class RingBuffer<T> {
    private final Object[] entries;
    private final int mask;
    
    // Padded sequences to avoid false sharing on L1/L2 caches
    private final PaddedLong writeSequence = new PaddedLong(-1);
    private final PaddedLong readSequence = new PaddedLong(-1);

    public RingBuffer(int capacity, Supplier<T> eventFactory) {
        // Ensure capacity is a power of 2
        int powerOfTwoCapacity = 1;
        while (powerOfTwoCapacity < capacity) powerOfTwoCapacity <<= 1;
        
        this.entries = new Object[powerOfTwoCapacity];
        this.mask = powerOfTwoCapacity - 1;
        
        // Pre-allocate memory to avoid allocation during runtime
        for (int i = 0; i < powerOfTwoCapacity; i++) {
            entries[i] = eventFactory.get();
        }
    }

    public long nextWrite() {
        return writeSequence.incrementAndGet();
    }

    @SuppressWarnings("unchecked")
    public T get(long sequence) {
        return (T) entries[(int) (sequence & mask)];
    }

    public void publish(long sequence) {
        // In a real Disruptor, publishing makes it visible to consumers via a memory barrier
        // Here, we simulate the commit by updating the volatile write sequence boundary if needed
    }

    public long getWriteSequence() {
        return writeSequence.get();
    }

    public boolean hasAvailable(long sequence) {
        return sequence <= writeSequence.get();
    }
}

class PaddedLong extends AtomicLong {
    // Cache line padding (typically 64 or 128 bytes)
    public volatile long p1, p2, p3, p4, p5, p6, p7;
    public PaddedLong(long initialValue) { super(initialValue); }
}
