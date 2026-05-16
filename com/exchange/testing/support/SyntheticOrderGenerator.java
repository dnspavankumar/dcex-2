package com.exchange.testing.support;

import com.exchange.matching.models.OrderSide;
import com.exchange.matching.models.OrderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SyntheticOrderGenerator {

    public List<OrderInstruction> marketMakerFlow(String symbol, int levels, double midPrice) {
        List<OrderInstruction> instructions = new ArrayList<>();
        for (int i = 0; i < levels; i++) {
            double offset = 5.0d + i;
            instructions.add(OrderInstruction.limit("mm-bid-" + i, symbol, OrderSide.BUY, 0.5d, midPrice - offset));
            instructions.add(OrderInstruction.limit("mm-ask-" + i, symbol, OrderSide.SELL, 0.5d, midPrice + offset));
        }
        return instructions;
    }

    public List<OrderInstruction> takerFlow(String symbol, int count, double price) {
        List<OrderInstruction> instructions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OrderSide side = i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL;
            double aggressivePrice = side == OrderSide.BUY ? price + 100.0d : price - 100.0d;
            instructions.add(OrderInstruction.limit("taker-" + i, symbol, side, 0.25d, aggressivePrice));
        }
        return instructions;
    }

    public List<OrderInstruction> cancelHeavyFlow(String symbol, int count, double price) {
        List<OrderInstruction> instructions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            instructions.add(OrderInstruction.limit("cancel-" + i, symbol, OrderSide.BUY, 0.1d, price - i));
        }
        return instructions;
    }

    public List<OrderInstruction> burstVolatilityFlow(String symbol, int burstSize, double anchorPrice) {
        List<OrderInstruction> instructions = new ArrayList<>();
        for (int i = 0; i < burstSize; i++) {
            OrderSide side = i % 3 == 0 ? OrderSide.BUY : OrderSide.SELL;
            double shock = (i % 25) * 7.5d;
            double price = side == OrderSide.BUY ? anchorPrice + shock : anchorPrice - shock;
            instructions.add(OrderInstruction.limit("burst-" + i, symbol, side, 0.2d + (i % 5) * 0.05d, price));
        }
        return instructions;
    }

    public List<OrderInstruction> randomizedFlow(String symbol, int count, long seed) {
        Random random = new Random(seed);
        List<OrderInstruction> instructions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
            double price = 50_000.0d + (random.nextInt(400) - 200);
            double quantity = 0.05d + random.nextInt(20) * 0.01d;
            instructions.add(new OrderInstruction("rnd-" + i, symbol, side, OrderType.LIMIT, quantity, price));
        }
        return instructions;
    }

    public record OrderInstruction(
            String user,
            String symbol,
            OrderSide side,
            OrderType type,
            double quantity,
            double price
    ) {
        public static OrderInstruction limit(String user, String symbol, OrderSide side, double quantity, double price) {
            return new OrderInstruction(user, symbol, side, OrderType.LIMIT, quantity, price);
        }
    }
}
