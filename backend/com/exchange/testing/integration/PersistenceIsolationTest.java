package com.exchange.testing.integration;

import com.exchange.common.config.ExchangeConfig;
import com.exchange.matching.models.Order;
import com.exchange.matching.models.OrderSide;
import com.exchange.matching.models.OrderType;
import com.exchange.observability.metrics.LatencyObservabilityFramework;
import com.exchange.storage.postgres.BatchPersistenceWorker;
import com.exchange.storage.postgres.NeonDbConnectionManager;
import com.exchange.testing.support.TestExchangeEngine;
import com.exchange.trade.execution.Trade;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceIsolationTest {
    @TempDir
    Path tempDir;

    // Using Neon database - hardcoded for testing
    private static final String DB_URL = "jdbc:postgresql://ep-little-recipe-apc6wr73-pooler.c-7.us-east-1.aws.neon.tech/neondb?user=neondb_owner&password=npg_XpMgC2ra5UAP&sslmode=require&channelBinding=require";

    @Test
    @DisplayName("Persistence outages and lag do not block the matching path")
    void laggingPersistenceRemainsIsolated() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder()
                .walDirectory(tempDir.resolve("wal"))
                .persistenceQueueCapacity(128)
                .persistenceDelay(Duration.ofMillis(20))
                .build()) {
            engine.pausePersistenceConsumer();
            engine.setPersistenceFailureMode(true);

            for (int i = 0; i < 2_500; i++) {
                engine.submitLimitOrder("BTC-USD", i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL, 0.02d, 50_000.0d + (i % 25), "pi-" + (i % 16));
            }

            assertTrue(engine.getGlobalSequenceId() >= 2_500L);
            assertTrue(engine.getPersistenceBacklog() > 0L || engine.getPersistenceDrops() > 0L);
        }
    }

    @Test
    @DisplayName("Batch persistence worker flushes trades and orders to Neon PostgreSQL")
    void flushesPersistenceBatchesIntoPostgres() throws Exception {
        ExchangeConfig config = new ExchangeConfig();
        config.getDatabase().setUrl(DB_URL);
        config.getDatabase().setBatchThreshold(1);
        config.getDatabase().setFlushIntervalMs(50L);

        try (var connection = DriverManager.getConnection(DB_URL);
             var stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS trade_history (trade_id BIGINT PRIMARY KEY, symbol TEXT, maker_order_id BIGINT, taker_order_id BIGINT, price NUMERIC, quantity NUMERIC, timestamp BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS order_history (order_id BIGINT PRIMARY KEY, sequence_id BIGINT, user_id BIGINT, symbol TEXT, side TEXT, type TEXT, price NUMERIC, quantity NUMERIC, status TEXT, timestamp BIGINT)");
        }

        BatchPersistenceWorker worker = new BatchPersistenceWorker(config, new LatencyObservabilityFramework(), new NeonDbConnectionManager(config));
        Thread thread = new Thread(worker, "persistence-worker-test");
        thread.start();

        try {
            worker.queueForPersistence(new Trade(1L, "BTC-USD", 10L, 11L, 1L, 2L, BigDecimal.valueOf(50_000L), BigDecimal.ONE));
            worker.queueForPersistence(new Order(12L, 3L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, BigDecimal.valueOf(50_010L), BigDecimal.ONE, 1L, 99L));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
                try (var connection = DriverManager.getConnection(DB_URL);
                     var tradeStmt = connection.createStatement();
                     var orderStmt = connection.createStatement();
                     var tradeRs = tradeStmt.executeQuery("SELECT COUNT(*) FROM trade_history");
                     var orderRs = orderStmt.executeQuery("SELECT COUNT(*) FROM order_history")) {
                    tradeRs.next();
                    orderRs.next();
                    return tradeRs.getInt(1) == 1 && orderRs.getInt(1) == 1;
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        } finally {
            worker.stop();
            thread.interrupt();
            thread.join(2_000L);
        }
    }
}
