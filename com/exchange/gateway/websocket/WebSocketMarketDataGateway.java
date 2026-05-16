package com.exchange.gateway.websocket;

import com.exchange.matching.models.Order;
import com.exchange.trade.execution.Trade;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Public-facing WebSocket Market Data Gateway.
 * Fully decoupled from the internal EventStreamer to prevent slow consumer network 
 * backpressure from cascading into the core matching engine's RingBuffer.
 * Uses high-frequency asynchronous broadcast threads.
 */
public class WebSocketMarketDataGateway {
    private static final Logger LOGGER = Logger.getLogger(WebSocketMarketDataGateway.class.getName());
    
    // Dedicated thread pool to push data to network sockets
    private final ExecutorService networkDispatcher = Executors.newFixedThreadPool(4);

    public void broadcastTradeEvent(Trade trade) {
        networkDispatcher.submit(() -> {
            // Encode using MessagePack or SBE here
            byte[] binaryFrame = encodeTradeSBE(trade);
            // Push binary frame to connected WebSocket clients via Netty/Undertow
            LOGGER.info("[Public WS] Pushed binary TradeEvent frame to clients for " + trade.getSymbol());
        });
    }

    public void broadcastOrderBookDelta(String symbol, Order order) {
        networkDispatcher.submit(() -> {
            byte[] binaryFrame = encodeOrderBookSBE(symbol, order);
            LOGGER.info("[Public WS] Pushed binary L2 Delta frame to clients for " + symbol);
        });
    }

    private byte[] encodeTradeSBE(Trade trade) {
        // Stub for Simple Binary Encoding (SBE)
        return new byte[]{0x01, 0x02};
    }

    private byte[] encodeOrderBookSBE(String symbol, Order order) {
        // Stub for Simple Binary Encoding (SBE)
        return new byte[]{0x03, 0x04};
    }
}
