package com.exchange.matching.models;

import java.math.BigDecimal;

public class Order {

    private final long orderId;
    private final long userId;

    private final String symbol;

    private final OrderSide side;
    private final OrderType type;

    private final BigDecimal price;

    private final BigDecimal quantity;

    private BigDecimal remainingQuantity;

    private final long timestamp;

    private final long sequenceId;

    private OrderStatus status;

    public Order(
            long orderId,
            long userId,
            String symbol,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            BigDecimal quantity,
            long timestamp,
            long sequenceId
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timestamp = timestamp;
        this.sequenceId = sequenceId;
        this.status = OrderStatus.OPEN;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getUserId() {
        return userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void reduceRemainingQuantity(BigDecimal executedQuantity) {
        this.remainingQuantity =
                this.remainingQuantity.subtract(executedQuantity);

        if (this.remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isFilled() {
        return this.status == OrderStatus.FILLED;
    }

    public boolean isBuy() {
        return this.side == OrderSide.BUY;
    }

    public boolean isSell() {
        return this.side == OrderSide.SELL;
    }

    @Override
    public String toString() {
        return "Order{" +
        "orderId=" + orderId +
        ", userId=" + userId +
        ", symbol='" + symbol + '\'' +
        ", side=" + side +
        ", type=" + type +
        ", price=" + price +
        ", quantity=" + quantity +
        ", remainingQuantity=" + remainingQuantity +
        ", timestamp=" + timestamp +
        ", sequenceId=" + sequenceId +
        ", status=" + status +
        '}';
    }
}