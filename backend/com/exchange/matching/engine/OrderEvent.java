package com.exchange.matching.engine;

import com.exchange.matching.models.Order;

/**
 * Pre-allocated wrapper for the RingBuffer to prevent JVM Garbage Collection overhead
 * on the hot path. We mutate this object instead of creating new ones.
 */
public class OrderEvent {
    private Order order;
    private long enqueueTimeNs;

    public void set(Order order, long enqueueTimeNs) {
        this.order = order;
        this.enqueueTimeNs = enqueueTimeNs;
    }

    public Order getOrder() {
        return order;
    }

    public long getEnqueueTimeNs() {
        return enqueueTimeNs;
    }

    public void clear() {
        this.order = null;
    }
}
