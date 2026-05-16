package com.exchange.testing.integration;

import com.exchange.gateway.dto.OrderGatewayServiceGrpc;
import com.exchange.gateway.dto.OrderProto;
import com.exchange.matching.models.OrderSide;
import com.exchange.testing.support.TestExchangeEngine;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcCommunicationTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Generated protobuf and in-process gRPC transport remain wire compatible")
    void verifiesGrpcTransportCorrectness() throws Exception {
        try (TestExchangeEngine engine = TestExchangeEngine.builder().walDirectory(tempDir.resolve("wal")).build()) {
            String serverName = InProcessServerBuilder.generateName();
            var server = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(new OrderGatewayServiceGrpc.OrderGatewayServiceImplBase() {
                        @Override
                        public void submitOrder(OrderProto.OrderRequest request, StreamObserver<OrderProto.OrderResponse> responseObserver) {
                            long orderId = engine.submitLimitOrder(
                                    request.getSymbol(),
                                    request.getSide() == OrderProto.OrderSide.BUY ? OrderSide.BUY : OrderSide.SELL,
                                    request.getQuantity(),
                                    request.getPrice(),
                                    "grpc-" + request.getUserId()
                            );
                            responseObserver.onNext(OrderProto.OrderResponse.newBuilder()
                                    .setAccepted(true)
                                    .setOrderId(orderId)
                                    .setSequenceId(engine.getOrderSequenceId(orderId))
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void cancelOrder(OrderProto.CancelRequest request, StreamObserver<OrderProto.CancelResponse> responseObserver) {
                            engine.cancelOrder(request.getOrderId(), "grpc-" + request.getUserId());
                            responseObserver.onNext(OrderProto.CancelResponse.newBuilder().setSuccess(true).build());
                            responseObserver.onCompleted();
                        }
                    })
                    .build()
                    .start();

            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            try {
                OrderGatewayServiceGrpc.OrderGatewayServiceBlockingStub stub = OrderGatewayServiceGrpc.newBlockingStub(channel);
                OrderProto.OrderResponse response = stub.submitOrder(OrderProto.OrderRequest.newBuilder()
                        .setUserId(101L)
                        .setSymbol("BTC-USD")
                        .setSide(OrderProto.OrderSide.BUY)
                        .setType(OrderProto.OrderType.LIMIT)
                        .setPrice(50_000.0d)
                        .setQuantity(0.25d)
                        .setClientOrderId(9_001L)
                        .build());

                OrderProto.CancelResponse cancelResponse = stub.cancelOrder(OrderProto.CancelRequest.newBuilder()
                        .setOrderId(response.getOrderId())
                        .setUserId(101L)
                        .setSymbol("BTC-USD")
                        .build());

                assertTrue(response.getAccepted());
                assertTrue(response.getSequenceId() > 0L);
                assertTrue(cancelResponse.getSuccess());
                assertEquals(engine.getGlobalSequenceId(), response.getSequenceId() + 1L);
            } finally {
                channel.shutdownNow();
                server.shutdownNow();
            }
        }
    }
}
