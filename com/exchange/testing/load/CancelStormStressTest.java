package com.exchange.testing.load;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancelStormStressTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Cancellation spikes do not destabilize the submission pipeline")
    void handlesCancelStorm() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            List<Long> orderIds = new ArrayList<>();
            for (int i = 0; i < 4_000; i++) {
                orderIds.add(engine.submitLimitOrder("BTC-USD", OrderSide.BUY, 0.05d, 49_900.0d + (i % 20), "cancel-" + (i % 64)));
            }
            for (int i = 0; i < 3_500; i++) {
                engine.cancelOrder(orderIds.get(i), "cancel-" + (i % 64));
            }

            assertEquals(7_500L, engine.getGlobalSequenceId());
            assertTrue(engine.getPersistenceBacklog() >= 0L);
            assertTrue(engine.getWebsocketBacklog() >= 0L);
        }
    }
}
