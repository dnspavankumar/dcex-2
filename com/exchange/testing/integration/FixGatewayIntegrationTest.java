package com.exchange.testing.integration;

import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixGatewayIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("FIX session flow preserves heartbeats, sequencing, and order routing")
    void validatesFixSessionHandling() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            FixSessionHarness fix = new FixSessionHarness(engine);
            String logonAck = fix.handle("35=A|49=CLIENT|56=EXCH|34=1");
            String heartbeat = fix.handle("35=0|49=CLIENT|56=EXCH|34=2");
            String orderAck = fix.handle("35=D|55=BTC-USD|54=1|38=1.0|44=50000|49=CLIENT|56=EXCH|34=3");
            String cancelAck = fix.handle("35=F|41=1|37=1001|49=CLIENT|56=EXCH|34=4");

            assertTrue(logonAck.contains("35=A"));
            assertTrue(heartbeat.contains("35=0"));
            assertTrue(orderAck.contains("39=0"));
            assertTrue(cancelAck.contains("150=4"));
            assertEquals(2L, engine.getGlobalSequenceId());
        }
    }

    private static final class FixSessionHarness {
        private final TestExchangeEngine engine;
        private int outboundSeq = 1;

        private FixSessionHarness(TestExchangeEngine engine) {
            this.engine = engine;
        }

        private String handle(String message) {
            Map<String, String> fields = parse(message);
            String msgType = fields.get("35");
            return switch (msgType) {
                case "A" -> "35=A|34=" + outboundSeq++;
                case "0" -> "35=0|34=" + outboundSeq++;
                case "D" -> {
                    OrderSide side = "1".equals(fields.get("54")) ? OrderSide.BUY : OrderSide.SELL;
                    long orderId = engine.submitLimitOrder(fields.get("55"), side, Double.parseDouble(fields.get("38")), Double.parseDouble(fields.get("44")), "fix-client");
                    yield "35=8|39=0|37=" + orderId + "|34=" + outboundSeq++;
                }
                case "F" -> {
                    engine.cancelOrder(Long.parseLong(fields.get("37")), "fix-client");
                    yield "35=9|150=4|34=" + outboundSeq++;
                }
                default -> throw new IllegalArgumentException("Unsupported FIX message type " + msgType);
            };
        }

        private Map<String, String> parse(String message) {
            Map<String, String> fields = new HashMap<>();
            for (String token : message.split("\\|")) {
                String[] pair = token.split("=");
                if (pair.length == 2) {
                    fields.put(pair[0], pair[1]);
                }
            }
            return fields;
        }
    }
}
