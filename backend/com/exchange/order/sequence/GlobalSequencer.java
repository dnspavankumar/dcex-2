package com.exchange.order.sequence;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Global Sequencer that assigns a globally deterministic monotonic sequence ID 
 * to every incoming order across all symbols. This is critical for deterministic replay.
 */
public class GlobalSequencer {
    private final AtomicLong currentSequenceId;

    public GlobalSequencer(long startId) {
        this.currentSequenceId = new AtomicLong(startId);
    }

    public long nextId() {
        return currentSequenceId.incrementAndGet();
    }
    
    public long getCurrentId() {
        return currentSequenceId.get();
    }
}
