package com.exchange.testing.fuzz;

import com.exchange.gateway.dto.OrderProto;
import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SerializationFuzzTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Malformed protobuf packets and WAL lines fail fast without hanging replay")
    void malformedBinaryInputsAreRejected() throws Exception {
        assertThrows(InvalidProtocolBufferException.class, () -> OrderProto.OrderRequest.parseFrom(new byte[]{0x01, 0x02, 0x03}));

        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            engine.submitLimitOrder("BTC-USD", OrderSide.BUY, 0.10d, 50_000.0d, "ser");
            engine.appendCorruptedWalLine("BROKEN|NOT|A|VALID|ENTRY");
        }

        try (TestExchangeEngine replay = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            assertThrows(IllegalArgumentException.class, replay::replayWalFromBeginning);
        }
    }
}
