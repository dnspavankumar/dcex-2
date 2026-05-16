package com.exchange.matching.engine;

import com.exchange.matching.models.Order;
import com.exchange.matching.orderbook.OrderBook;
import com.exchange.stream.events.EventStreamer;
import com.exchange.trade.execution.TradeExecutionService;
import com.exchange.observability.metrics.LatencyObservabilityFramework;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordinator to manage multiple symbol matching engines.
 * Routes incoming orders to the appropriate engine.
 */
public class MatchingEngineCoordinator {

    private final Map<String, SymbolMatchingEngine> engines;
    private final ExecutorService executorService;
    private final TradeExecutionService tradeExecutionService;
    private final EventStreamer eventStreamer;
    private final LatencyObservabilityFramework latencyMetrics;

    public MatchingEngineCoordinator(TradeExecutionService tradeExecutionService, 
                                     EventStreamer eventStreamer,
                                     LatencyObservabilityFramework latencyMetrics) {
        this.engines = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
        this.tradeExecutionService = tradeExecutionService;
        this.eventStreamer = eventStreamer;
        this.latencyMetrics = latencyMetrics;
    }

    public void registerSymbol(String symbol) {
        engines.computeIfAbsent(symbol, s -> {
            OrderBook orderBook = new OrderBook(symbol);
            SymbolMatchingEngine engine = new SymbolMatchingEngine(
                orderBook, tradeExecutionService, eventStreamer, latencyMetrics
            );
            executorService.submit(engine);
            return engine;
        });
    }

    public void submitOrder(Order order) {
        SymbolMatchingEngine engine = engines.get(order.getSymbol());
        if (engine == null) {
            throw new IllegalArgumentException("No matching engine found for symbol: " + order.getSymbol());
        }
        engine.submitOrder(order);
    }
    
    public SymbolMatchingEngine getEngine(String symbol) {
        return engines.get(symbol);
    }

    public void shutdown() {
        engines.values().forEach(SymbolMatchingEngine::stop);
        executorService.shutdown();
    }
}
