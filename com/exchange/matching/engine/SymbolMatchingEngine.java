package com.exchange.matching.engine;

import com.exchange.common.concurrency.RingBuffer;
import com.exchange.matching.models.Order;
import com.exchange.matching.models.OrderSide;
import com.exchange.matching.models.OrderType;
import com.exchange.matching.orderbook.OrderBook;
import com.exchange.stream.events.EventStreamer;
import com.exchange.trade.execution.Trade;
import com.exchange.trade.execution.TradeExecutionService;
import com.exchange.observability.metrics.LatencyObservabilityFramework;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-Frequency Trading (HFT) grade deterministic matching engine.
 * Employs a single-threaded Disruptor-style RingBuffer per symbol.
 * Completely lock-free on the consumption path.
 */
public class SymbolMatchingEngine implements Runnable {
    
    private final OrderBook orderBook;
    private final TradeExecutionService tradeExecutionService;
    private final EventStreamer eventStreamer;
    private final LatencyObservabilityFramework latencyMetrics;
    
    // Lock-free RingBuffer replacing LinkedBlockingQueue
    private final RingBuffer<OrderEvent> ringBuffer;
    private long readSequence = 0;
    
    private final AtomicLong tradeIdGenerator = new AtomicLong(1);
    private volatile boolean isRunning = true;

    public SymbolMatchingEngine(OrderBook orderBook, TradeExecutionService tradeExecutionService, 
                                EventStreamer eventStreamer, LatencyObservabilityFramework latencyMetrics) {
        this.orderBook = orderBook;
        this.tradeExecutionService = tradeExecutionService;
        this.eventStreamer = eventStreamer;
        this.latencyMetrics = latencyMetrics;
        // Pre-allocate 16,384 events
        this.ringBuffer = new RingBuffer<>(16384, OrderEvent::new); 
    }

    public void submitOrder(Order order) {
        long seq = ringBuffer.nextWrite();
        OrderEvent event = ringBuffer.get(seq);
        event.set(order, System.nanoTime()); // Track entry time for latency measurement
        ringBuffer.publish(seq);
    }

    public void stop() {
        isRunning = false;
    }

    @Override
    public void run() {
        // Spin-wait loop: Ultra-low latency, keeps CPU core hot
        while (isRunning) {
            if (ringBuffer.hasAvailable(readSequence)) {
                OrderEvent event = ringBuffer.get(readSequence);
                long matchStartTime = System.nanoTime();
                
                // Track Queue Depth Delay
                latencyMetrics.recordQueueLatency(matchStartTime - event.getEnqueueTimeNs());
                
                processOrder(event.getOrder());
                
                // Track Matching Execution Delay
                latencyMetrics.recordMatchingLatency(System.nanoTime() - matchStartTime);
                
                event.clear(); // Help GC just in case
                readSequence++;
            } else {
                Thread.onSpinWait(); 
            }
        }
    }

    private void processOrder(Order takerOrder) {
        if (takerOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) return;

        matchOrder(takerOrder);

        if (takerOrder.getType() == OrderType.LIMIT && !takerOrder.isFilled()) {
            orderBook.addOrder(takerOrder);
            eventStreamer.publishOrderBookUpdate(orderBook.getSymbol(), takerOrder);
        } else if (takerOrder.getType() == OrderType.MARKET && !takerOrder.isFilled()) {
            takerOrder.cancel();
            eventStreamer.publishOrderCompletion(takerOrder);
        }

        if (takerOrder.isFilled()) {
            eventStreamer.publishOrderCompletion(takerOrder);
        }
    }

    private void matchOrder(Order takerOrder) {
        boolean isBuy = takerOrder.isBuy();

        while (!takerOrder.isFilled()) {
            Order makerOrder = isBuy ? orderBook.getBestAsk() : orderBook.getBestBid();
            if (makerOrder == null) break; 

            if (takerOrder.getType() == OrderType.LIMIT) {
                if (isBuy && takerOrder.getPrice().compareTo(makerOrder.getPrice()) < 0) break; 
                if (!isBuy && takerOrder.getPrice().compareTo(makerOrder.getPrice()) > 0) break; 
            }

            BigDecimal matchQuantity = takerOrder.getRemainingQuantity().min(makerOrder.getRemainingQuantity());
            BigDecimal matchPrice = makerOrder.getPrice();

            Trade trade = new Trade(
                    tradeIdGenerator.getAndIncrement(),
                    takerOrder.getSymbol(),
                    makerOrder.getOrderId(),
                    takerOrder.getOrderId(),
                    makerOrder.getUserId(),
                    takerOrder.getUserId(),
                    matchPrice,
                    matchQuantity
            );

            takerOrder.reduceRemainingQuantity(matchQuantity);
            makerOrder.reduceRemainingQuantity(matchQuantity);

            if (makerOrder.isFilled()) {
                if (isBuy) orderBook.pollBestAsk();
                else orderBook.pollBestBid();
                orderBook.removeOrder(makerOrder.getOrderId());
                
                eventStreamer.publishOrderBookUpdate(orderBook.getSymbol(), makerOrder);
                eventStreamer.publishOrderCompletion(makerOrder);
            }

            tradeExecutionService.executeTrade(trade, makerOrder, takerOrder);
        }
    }
}
