package com.exchange.matching.orderbook;

import com.exchange.matching.models.Order;
import com.exchange.matching.models.OrderSide;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OrderBook implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String symbol;

    // Price-Time priority
    // Buys: Highest price first, then earliest time
    private final PriorityQueue<Order> bids;
    // Sells: Lowest price first, then earliest time
    private final PriorityQueue<Order> asks;

    // Fast lookup for order management
    private final Map<Long, Order> orderMap;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        
        Comparator<Order> bidComparator = Comparator
                .comparing(Order::getPrice).reversed()
                .thenComparing(Order::getTimestamp);
                
        Comparator<Order> askComparator = Comparator
                .comparing(Order::getPrice)
                .thenComparing(Order::getTimestamp);
                
        this.bids = new PriorityQueue<>(bidComparator);
        this.asks = new PriorityQueue<>(askComparator);
        this.orderMap = new ConcurrentHashMap<>();
    }

    public synchronized void addOrder(Order order) {
        if (order.getSide() == OrderSide.BUY) {
            bids.add(order);
        } else {
            asks.add(order);
        }
        orderMap.put(order.getOrderId(), order);
    }

    public synchronized void removeOrder(long orderId) {
        Order order = orderMap.remove(orderId);
        if (order != null) {
            if (order.getSide() == OrderSide.BUY) {
                bids.remove(order);
            } else {
                asks.remove(order);
            }
            order.cancel();
        }
    }

    public synchronized Order getBestBid() {
        return bids.peek();
    }

    public synchronized Order getBestAsk() {
        return asks.peek();
    }
    
    public synchronized Order pollBestBid() {
        return bids.poll();
    }
    
    public synchronized Order pollBestAsk() {
        return asks.poll();
    }

    public String getSymbol() {
        return symbol;
    }

    public synchronized List<Order> getBidSnapshot() {
        return bids.stream().collect(Collectors.toList());
    }

    public synchronized List<Order> getAskSnapshot() {
        return asks.stream().collect(Collectors.toList());
    }
    
    public synchronized Map<Long, Order> getOrderMapSnapshot() {
        return Map.copyOf(orderMap);
    }
}
