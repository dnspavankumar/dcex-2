package com.exchange.stream.events;

import com.exchange.gateway.websocket.WebSocketMarketDataGateway;
import com.exchange.matching.models.Order;
import com.exchange.storage.postgres.BatchPersistenceWorker;
import com.exchange.trade.execution.Trade;

import java.util.logging.Logger;

/**
 * Highly decoupled Event Bus routing core matching events to asynchronous consumers.
 * Guarantees that neither WebSocket backpressure nor slow PostgreSQL inserts 
 * can ever block the HFT execution pipeline.
 */
public class EventStreamer {
    private static final Logger LOGGER = Logger.getLogger(EventStreamer.class.getName());

    private final WebSocketMarketDataGateway webSocketGateway;
    private final BatchPersistenceWorker persistenceWorker;

    public EventStreamer(WebSocketMarketDataGateway webSocketGateway, BatchPersistenceWorker persistenceWorker) {
        this.webSocketGateway = webSocketGateway;
        this.persistenceWorker = persistenceWorker;
    }

    public void publishTradeEvent(Trade trade) {
        // 1. Broadcast to high-frequency WebSocket subscribers
        webSocketGateway.broadcastTradeEvent(trade);
        
        // 2. Queue for durable historical persistence
        persistenceWorker.queueForPersistence(trade);
    }

    public void publishOrderBookUpdate(String symbol, Order order) {
        // We broadcast L2 Deltas to users, but we DO NOT persist these heavily mutating events
        // to PostgreSQL. Memory snapshots and WAL already guarantee disaster recovery.
        webSocketGateway.broadcastOrderBookDelta(symbol, order);
    }

    public void publishOrderCompletion(Order order) {
        // Persist final states of orders for user history / audit logs
        persistenceWorker.queueForPersistence(order);
    }

    public void publishTickerUpdate(String symbol, Trade trade) {
        // Statistics logic goes here (could be a memory aggregation worker)
    }
}
