package com.exchange.testing.load;

import com.exchange.testing.support.SyntheticOrderGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticOrderGeneratorTest {
    private final SyntheticOrderGenerator generator = new SyntheticOrderGenerator();

    @Test
    @DisplayName("Synthetic flow generator produces realistic maker, taker, cancel-heavy, and burst mixes")
    void generatesRepresentativeOrderFlows() {
        var makerFlow = generator.marketMakerFlow("BTC-USD", 25, 50_000.0d);
        var takerFlow = generator.takerFlow("BTC-USD", 80, 50_000.0d);
        var cancelHeavyFlow = generator.cancelHeavyFlow("BTC-USD", 120, 49_950.0d);
        var burstFlow = generator.burstVolatilityFlow("BTC-USD", 200, 50_000.0d);

        assertEquals(50, makerFlow.size());
        assertEquals(80, takerFlow.size());
        assertEquals(120, cancelHeavyFlow.size());
        assertEquals(200, burstFlow.size());
        assertTrue(takerFlow.stream().anyMatch(order -> order.price() > 50_000.0d));
        assertTrue(burstFlow.stream().anyMatch(order -> order.price() < 49_900.0d));
    }
}
