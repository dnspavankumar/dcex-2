package com.exchange.risk.realtime;

import com.exchange.matching.models.Order;
import com.exchange.matching.models.OrderSide;
import com.exchange.wallet.balances.BalanceManager;

import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * Validates orders before they are sequenced and matched.
 * Performs balance checks, limits, and locks the necessary funds.
 */
public class PreTradeRiskEngine {
    private static final Logger LOGGER = Logger.getLogger(PreTradeRiskEngine.class.getName());
    private final BalanceManager balanceManager;

    private static final BigDecimal MIN_ORDER_VALUE = new BigDecimal("10.00"); // Example 10 USD min

    public PreTradeRiskEngine(BalanceManager balanceManager) {
        this.balanceManager = balanceManager;
    }

    /**
     * @return true if the order passes risk checks and funds are successfully locked.
     */
    public boolean validateAndLock(Order order) {
        String[] assets = order.getSymbol().split("_");
        if (assets.length != 2) return false;

        String baseAsset = assets[0];
        String quoteAsset = assets[1];

        BigDecimal orderValue = order.getPrice().multiply(order.getQuantity());

        // 1. Min Size / Exposure check
        if (orderValue.compareTo(MIN_ORDER_VALUE) < 0) {
            LOGGER.warning("Order rejected: Value " + orderValue + " is below minimum " + MIN_ORDER_VALUE);
            return false;
        }

        // 2. Balance Verification and Locking
        boolean locked;
        if (order.getSide() == OrderSide.BUY) {
            // Buyer needs Quote Asset (e.g. USD)
            locked = balanceManager.lockBalance(order.getUserId(), quoteAsset, orderValue);
            if (!locked) {
                LOGGER.warning("Order rejected: Insufficient " + quoteAsset + " balance for User " + order.getUserId());
            }
        } else {
            // Seller needs Base Asset (e.g. BTC)
            locked = balanceManager.lockBalance(order.getUserId(), baseAsset, order.getQuantity());
             if (!locked) {
                LOGGER.warning("Order rejected: Insufficient " + baseAsset + " balance for User " + order.getUserId());
            }
        }

        return locked;
    }
}
