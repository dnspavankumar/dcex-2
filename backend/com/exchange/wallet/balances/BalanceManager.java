package com.exchange.wallet.balances;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user balances in memory for fast, concurrent updates.
 * Like the OrderBook, this could be periodically backed up or synchronously saved
 * in a snapshot or event-sourcing model.
 */
public class BalanceManager {

    // Map of UserId -> (AssetSymbol -> Balance)
    private final Map<Long, Map<String, BigDecimal>> userBalances = new ConcurrentHashMap<>();
    
    // Locked/Reserved balances for open orders
    private final Map<Long, Map<String, BigDecimal>> lockedBalances = new ConcurrentHashMap<>();

    public void deposit(long userId, String asset, BigDecimal amount) {
        userBalances.putIfAbsent(userId, new ConcurrentHashMap<>());
        userBalances.get(userId).merge(asset, amount, BigDecimal::add);
    }

    public boolean withdraw(long userId, String asset, BigDecimal amount) {
        Map<String, BigDecimal> balances = userBalances.get(userId);
        if (balances == null) return false;

        BigDecimal currentBalance = balances.getOrDefault(asset, BigDecimal.ZERO);
        if (currentBalance.compareTo(amount) >= 0) {
            balances.put(asset, currentBalance.subtract(amount));
            return true;
        }
        return false;
    }

    public boolean lockBalance(long userId, String asset, BigDecimal amount) {
        Map<String, BigDecimal> balances = userBalances.get(userId);
        if (balances == null) return false;

        BigDecimal currentBalance = balances.getOrDefault(asset, BigDecimal.ZERO);
        if (currentBalance.compareTo(amount) >= 0) {
            // Deduct from available
            balances.put(asset, currentBalance.subtract(amount));
            
            // Add to locked
            lockedBalances.putIfAbsent(userId, new ConcurrentHashMap<>());
            lockedBalances.get(userId).merge(asset, amount, BigDecimal::add);
            return true;
        }
        return false;
    }

    public void unlockBalance(long userId, String asset, BigDecimal amount) {
        Map<String, BigDecimal> locked = lockedBalances.get(userId);
        if (locked != null && locked.getOrDefault(asset, BigDecimal.ZERO).compareTo(amount) >= 0) {
            // Deduct from locked
            locked.put(asset, locked.get(asset).subtract(amount));
            
            // Add back to available
            userBalances.putIfAbsent(userId, new ConcurrentHashMap<>());
            userBalances.get(userId).merge(asset, amount, BigDecimal::add);
        }
    }

    public BigDecimal getAvailableBalance(long userId, String asset) {
        Map<String, BigDecimal> balances = userBalances.get(userId);
        return balances != null ? balances.getOrDefault(asset, BigDecimal.ZERO) : BigDecimal.ZERO;
    }
    
    public BigDecimal getLockedBalance(long userId, String asset) {
        Map<String, BigDecimal> locked = lockedBalances.get(userId);
        return locked != null ? locked.getOrDefault(asset, BigDecimal.ZERO) : BigDecimal.ZERO;
    }
}
