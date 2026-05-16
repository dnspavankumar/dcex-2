package com.exchange.trade.execution;

import com.exchange.matching.models.Order;
import com.exchange.matching.models.OrderSide;
import com.exchange.stream.events.EventStreamer;
import com.exchange.wallet.balances.BalanceManager;

import java.math.BigDecimal;
import java.util.logging.Logger;

public class TradeExecutionService {
    
    private static final Logger LOGGER = Logger.getLogger(TradeExecutionService.class.getName());
    private final BalanceManager balanceManager;
    private final EventStreamer eventStreamer;

    public TradeExecutionService(BalanceManager balanceManager, EventStreamer eventStreamer) {
        this.balanceManager = balanceManager;
        this.eventStreamer = eventStreamer;
    }

    public void executeTrade(Trade trade, Order makerOrder, Order takerOrder) {
        String[] assets = trade.getSymbol().split("_");
        if (assets.length != 2) return;

        String baseAsset = assets[0];
        String quoteAsset = assets[1];

        BigDecimal tradeQuantity = trade.getQuantity();
        BigDecimal tradePrice = trade.getPrice();
        BigDecimal quoteAmount = tradeQuantity.multiply(tradePrice);

        long buyerUserId = (makerOrder.getSide() == OrderSide.BUY) ? makerOrder.getUserId() : takerOrder.getUserId();
        long sellerUserId = (makerOrder.getSide() == OrderSide.SELL) ? makerOrder.getUserId() : takerOrder.getUserId();

        try {
            balanceManager.unlockBalance(sellerUserId, baseAsset, tradeQuantity);
            balanceManager.withdraw(sellerUserId, baseAsset, tradeQuantity);
            balanceManager.deposit(sellerUserId, quoteAsset, quoteAmount);

            balanceManager.unlockBalance(buyerUserId, quoteAsset, quoteAmount);
            balanceManager.withdraw(buyerUserId, quoteAsset, quoteAmount);
            balanceManager.deposit(buyerUserId, baseAsset, tradeQuantity);
            
            LOGGER.info(String.format("Trade executed: ID %d | %s | Price %s | Qty %s", 
                    trade.getTradeId(), trade.getSymbol(), tradePrice, tradeQuantity));

            // Publish WebSocket Events
            eventStreamer.publishTradeEvent(trade);
            eventStreamer.publishTickerUpdate(trade.getSymbol(), trade);

        } catch (Exception e) {
            LOGGER.severe("Failed to execute trade: " + trade.getTradeId());
        }
    }
}
