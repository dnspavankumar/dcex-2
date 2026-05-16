package com.exchange.matching.engine;

import com.exchange.matching.models.Order;
import com.exchange.order.journal.WriteAheadLog;
import com.exchange.order.sequence.GlobalSequencer;
import com.exchange.risk.realtime.PreTradeRiskEngine;
import com.exchange.observability.metrics.LatencyObservabilityFramework;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The unified entrypoint for the exchange core.
 * Ensures the strict deterministic lifecycle of an order:
 * 1. Risk Check -> 2. Sequence -> 3. WAL Fsync -> 4. Matching Engine
 */
public class ExchangeCore {
    private static final Logger LOGGER = Logger.getLogger(ExchangeCore.class.getName());

    private final PreTradeRiskEngine riskEngine;
    private final GlobalSequencer sequencer;
    private final WriteAheadLog wal;
    private final MatchingEngineCoordinator coordinator;
    private final LatencyObservabilityFramework latencyMetrics;

    public ExchangeCore(PreTradeRiskEngine riskEngine, GlobalSequencer sequencer, 
                        WriteAheadLog wal, MatchingEngineCoordinator coordinator,
                        LatencyObservabilityFramework latencyMetrics) {
        this.riskEngine = riskEngine;
        this.sequencer = sequencer;
        this.wal = wal;
        this.coordinator = coordinator;
        this.latencyMetrics = latencyMetrics;
    }

    public void submitOrder(Order order) {
        if (!riskEngine.validateAndLock(order)) {
            return;
        }

        long sequenceId = sequencer.nextId();
        Order sequencedOrder = new Order(
                order.getOrderId(), order.getUserId(), order.getSymbol(),
                order.getSide(), order.getType(), order.getPrice(),
                order.getQuantity(), order.getTimestamp(), sequenceId
        );

        try {
            wal.append(sequencedOrder);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "CRITICAL: Failed to write order to WAL. Dropping order.", e);
            return;
        }

        coordinator.registerSymbol(sequencedOrder.getSymbol());
        coordinator.submitOrder(sequencedOrder);
    }
}
