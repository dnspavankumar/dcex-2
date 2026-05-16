package com.exchange.testing.support;

import com.exchange.matching.models.OrderSide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class MarketDataReconstructor {
    private final NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>();

    public void apply(TestExchangeEngine.MarketDataDelta delta) {
        NavigableMap<BigDecimal, BigDecimal> side = delta.side() == OrderSide.BUY ? bids : asks;
        if (delta.totalQuantityAtLevel().compareTo(BigDecimal.ZERO) <= 0) {
            side.remove(delta.price());
            return;
        }
        side.put(delta.price(), delta.totalQuantityAtLevel());
    }

    public TestExchangeEngine.BookDepthSnapshot snapshot(String symbol, int levels) {
        return new TestExchangeEngine.BookDepthSnapshot(symbol, toLevels(bids, levels), toLevels(asks, levels));
    }

    private List<TestExchangeEngine.PriceLevel> toLevels(NavigableMap<BigDecimal, BigDecimal> side, int levels) {
        List<TestExchangeEngine.PriceLevel> result = new ArrayList<>();
        for (var entry : side.entrySet()) {
            if (result.size() >= levels) {
                break;
            }
            result.add(new TestExchangeEngine.PriceLevel(entry.getKey(), entry.getValue(), 1));
        }
        return result;
    }
}
