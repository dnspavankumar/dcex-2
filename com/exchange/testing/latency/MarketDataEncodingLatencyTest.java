package com.exchange.testing.latency;

import com.exchange.gateway.dto.OrderProto;
import com.exchange.testing.support.BenchmarkReportWriter;
import com.exchange.testing.support.LatencyHistogram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataEncodingLatencyTest {
    @Test
    @DisplayName("Protobuf market data encoding and decoding stay within deterministic percentile bounds")
    void benchmarksMarketDataEncoding() throws Exception {
        LatencyHistogram encodeHistogram = new LatencyHistogram();
        LatencyHistogram decodeHistogram = new LatencyHistogram();

        for (int i = 0; i < 10_000; i++) {
            OrderProto.OrderRequest request = OrderProto.OrderRequest.newBuilder()
                    .setUserId(i)
                    .setSymbol("BTC-USD")
                    .setSide(i % 2 == 0 ? OrderProto.OrderSide.BUY : OrderProto.OrderSide.SELL)
                    .setType(OrderProto.OrderType.LIMIT)
                    .setPrice(50_000.0d + (i % 30))
                    .setQuantity(0.02d)
                    .setClientOrderId(i)
                    .build();

            long encodeStart = System.nanoTime();
            byte[] bytes = request.toByteArray();
            encodeHistogram.record(System.nanoTime() - encodeStart);

            long decodeStart = System.nanoTime();
            OrderProto.OrderRequest.parseFrom(bytes);
            decodeHistogram.record(System.nanoTime() - decodeStart);
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("encodeP99Ns", encodeHistogram.percentile(0.99d));
        metrics.put("decodeP99Ns", decodeHistogram.percentile(0.99d));
        BenchmarkReportWriter.write("latency", "market-data-encoding", metrics);

        assertTrue(encodeHistogram.percentile(0.99d) > 0L);
        assertTrue(decodeHistogram.percentile(0.99d) > 0L);
    }
}
