package com.exchange.trade.execution;

import java.math.BigDecimal;

public class Trade {
    private final long tradeId;
    private final String symbol;
    private final long makerOrderId;
    private final long takerOrderId;
    private final long makerUserId;
    private final long takerUserId;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final long timestamp;

    public Trade(long tradeId, String symbol, long makerOrderId, long takerOrderId, 
                 long makerUserId, long takerUserId, BigDecimal price, BigDecimal quantity) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.makerOrderId = makerOrderId;
        this.takerOrderId = takerOrderId;
        this.makerUserId = makerUserId;
        this.takerUserId = takerUserId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = System.currentTimeMillis();
    }

    public long getTradeId() { return tradeId; }
    public String getSymbol() { return symbol; }
    public long getMakerOrderId() { return makerOrderId; }
    public long getTakerOrderId() { return takerOrderId; }
    public long getMakerUserId() { return makerUserId; }
    public long getTakerUserId() { return takerUserId; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public long getTimestamp() { return timestamp; }
}
