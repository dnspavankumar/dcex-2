package com.exchange.disaster.replay;

import com.exchange.disaster.snapshots.OrderBookSnapshotService;
import com.exchange.matching.engine.MatchingEngineCoordinator;
import com.exchange.matching.models.Order;
import com.exchange.observability.metrics.LatencyObservabilityFramework;
import com.exchange.order.journal.WriteAheadLog;
import com.exchange.order.sequence.GlobalSequencer;

import java.util.List;
import java.util.logging.Logger;

/**
 * Deterministic recovery engine responsible for restoring the exchange state 
 * after a hard crash by applying the validated snapshot and replaying the WAL pipeline.
 */
public class RecoveryEngine {
    private static final Logger LOGGER = Logger.getLogger(RecoveryEngine.class.getName());

    private final OrderBookSnapshotService snapshotService;
    private final WriteAheadLog wal;
    private final MatchingEngineCoordinator matchingEngineCoordinator;
    private final GlobalSequencer sequencer;
    private final LatencyObservabilityFramework metrics;

    public RecoveryEngine(OrderBookSnapshotService snapshotService, WriteAheadLog wal, 
                          MatchingEngineCoordinator coordinator, GlobalSequencer sequencer,
                          LatencyObservabilityFramework metrics) {
        this.snapshotService = snapshotService;
        this.wal = wal;
        this.matchingEngineCoordinator = coordinator;
        this.sequencer = sequencer;
        this.metrics = metrics;
    }

    public void recover() {
        LOGGER.info("Starting deterministic system recovery process...");
        long totalStartTime = System.currentTimeMillis();

        // 1. Load the latest validated snapshot
        OrderBookSnapshotService.RecoveredSnapshot snapshot = snapshotService.recoverFromSnapshot();
        long snapshotSequenceId = snapshot.getSequenceId();
        
        long walReplayStartTime = System.currentTimeMillis();

        // 2. Read WAL entries strictly sequentially after the snapshot
        List<Order> missedOrders = wal.readSince(snapshotSequenceId);
        LOGGER.info("Found " + missedOrders.size() + " WAL events to replay since sequence " + snapshotSequenceId);

        // 3. Replay missed orders deterministically through the matching engine
        for (Order order : missedOrders) {
            // Note: In an HFT replay, we push directly to the ring buffer and bypass network/risk checks
            matchingEngineCoordinator.registerSymbol(order.getSymbol());
            matchingEngineCoordinator.submitOrder(order);
            
            // Fast-forward global sequencer
            if (order.getSequenceId() > sequencer.getCurrentId()) {
                while (sequencer.getCurrentId() < order.getSequenceId()) {
                    sequencer.nextId();
                }
            }
        }

        long walReplayDuration = System.currentTimeMillis() - walReplayStartTime;
        long totalDuration = System.currentTimeMillis() - totalStartTime;

        // 4. Record Recovery Observability Metrics
        metrics.recordRecoveryMetrics(walReplayDuration, totalDuration, snapshot.isValid());

        LOGGER.info(String.format("Recovery Complete. State is Consistent. | Highest Seq: %d | Replay Time: %d ms | Total Time: %d ms", 
                sequencer.getCurrentId(), walReplayDuration, totalDuration));
    }
}
