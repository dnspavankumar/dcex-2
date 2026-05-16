package com.exchange.gateway.dto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * gRPC Protocol Buffers definitions replacing JSON for internal 
 * microservice-to-microservice communication
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.66.0)",
    comments = "Source: exchange/gateway/dto/OrderProto.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class OrderGatewayServiceGrpc {

  private OrderGatewayServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "com.exchange.gateway.dto.OrderGatewayService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.exchange.gateway.dto.OrderProto.OrderRequest,
      com.exchange.gateway.dto.OrderProto.OrderResponse> getSubmitOrderMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SubmitOrder",
      requestType = com.exchange.gateway.dto.OrderProto.OrderRequest.class,
      responseType = com.exchange.gateway.dto.OrderProto.OrderResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.exchange.gateway.dto.OrderProto.OrderRequest,
      com.exchange.gateway.dto.OrderProto.OrderResponse> getSubmitOrderMethod() {
    io.grpc.MethodDescriptor<com.exchange.gateway.dto.OrderProto.OrderRequest, com.exchange.gateway.dto.OrderProto.OrderResponse> getSubmitOrderMethod;
    if ((getSubmitOrderMethod = OrderGatewayServiceGrpc.getSubmitOrderMethod) == null) {
      synchronized (OrderGatewayServiceGrpc.class) {
        if ((getSubmitOrderMethod = OrderGatewayServiceGrpc.getSubmitOrderMethod) == null) {
          OrderGatewayServiceGrpc.getSubmitOrderMethod = getSubmitOrderMethod =
              io.grpc.MethodDescriptor.<com.exchange.gateway.dto.OrderProto.OrderRequest, com.exchange.gateway.dto.OrderProto.OrderResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SubmitOrder"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.gateway.dto.OrderProto.OrderRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.gateway.dto.OrderProto.OrderResponse.getDefaultInstance()))
              .setSchemaDescriptor(new OrderGatewayServiceMethodDescriptorSupplier("SubmitOrder"))
              .build();
        }
      }
    }
    return getSubmitOrderMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.exchange.gateway.dto.OrderProto.CancelRequest,
      com.exchange.gateway.dto.OrderProto.CancelResponse> getCancelOrderMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CancelOrder",
      requestType = com.exchange.gateway.dto.OrderProto.CancelRequest.class,
      responseType = com.exchange.gateway.dto.OrderProto.CancelResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.exchange.gateway.dto.OrderProto.CancelRequest,
      com.exchange.gateway.dto.OrderProto.CancelResponse> getCancelOrderMethod() {
    io.grpc.MethodDescriptor<com.exchange.gateway.dto.OrderProto.CancelRequest, com.exchange.gateway.dto.OrderProto.CancelResponse> getCancelOrderMethod;
    if ((getCancelOrderMethod = OrderGatewayServiceGrpc.getCancelOrderMethod) == null) {
      synchronized (OrderGatewayServiceGrpc.class) {
        if ((getCancelOrderMethod = OrderGatewayServiceGrpc.getCancelOrderMethod) == null) {
          OrderGatewayServiceGrpc.getCancelOrderMethod = getCancelOrderMethod =
              io.grpc.MethodDescriptor.<com.exchange.gateway.dto.OrderProto.CancelRequest, com.exchange.gateway.dto.OrderProto.CancelResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CancelOrder"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.gateway.dto.OrderProto.CancelRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.gateway.dto.OrderProto.CancelResponse.getDefaultInstance()))
              .setSchemaDescriptor(new OrderGatewayServiceMethodDescriptorSupplier("CancelOrder"))
              .build();
        }
      }
    }
    return getCancelOrderMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static OrderGatewayServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<OrderGatewayServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<OrderGatewayServiceStub>() {
        @java.lang.Override
        public OrderGatewayServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new OrderGatewayServiceStub(channel, callOptions);
        }
      };
    return OrderGatewayServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static OrderGatewayServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<OrderGatewayServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<OrderGatewayServiceBlockingStub>() {
        @java.lang.Override
        public OrderGatewayServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new OrderGatewayServiceBlockingStub(channel, callOptions);
        }
      };
    return OrderGatewayServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static OrderGatewayServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<OrderGatewayServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<OrderGatewayServiceFutureStub>() {
        @java.lang.Override
        public OrderGatewayServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new OrderGatewayServiceFutureStub(channel, callOptions);
        }
      };
    return OrderGatewayServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * gRPC Protocol Buffers definitions replacing JSON for internal 
   * microservice-to-microservice communication
   * </pre>
   */
  public interface AsyncService {

    /**
     */
    default void submitOrder(com.exchange.gateway.dto.OrderProto.OrderRequest request,
        io.grpc.stub.StreamObserver<com.exchange.gateway.dto.OrderProto.OrderResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubmitOrderMethod(), responseObserver);
    }

    /**
     */
    default void cancelOrder(com.exchange.gateway.dto.OrderProto.CancelRequest request,
        io.grpc.stub.StreamObserver<com.exchange.gateway.dto.OrderProto.CancelResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCancelOrderMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service OrderGatewayService.
   * <pre>
   * gRPC Protocol Buffers definitions replacing JSON for internal 
   * microservice-to-microservice communication
   * </pre>
   */
  public static abstract class OrderGatewayServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return OrderGatewayServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service OrderGatewayService.
   * <pre>
   * gRPC Protocol Buffers definitions replacing JSON for internal 
   * microservice-to-microservice communication
   * </pre>
   */
  public static final class OrderGatewayServiceStub
      extends io.grpc.stub.AbstractAsyncStub<OrderGatewayServiceStub> {
    private OrderGatewayServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected OrderGatewayServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new OrderGatewayServiceStub(channel, callOptions);
    }

    /**
     */
    public void submitOrder(com.exchange.gateway.dto.OrderProto.OrderRequest request,
        io.grpc.stub.StreamObserver<com.exchange.gateway.dto.OrderProto.OrderResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSubmitOrderMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void cancelOrder(com.exchange.gateway.dto.OrderProto.CancelRequest request,
        io.grpc.stub.StreamObserver<com.exchange.gateway.dto.OrderProto.CancelResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCancelOrderMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service OrderGatewayService.
   * <pre>
   * gRPC Protocol Buffers definitions replacing JSON for internal 
   * microservice-to-microservice communication
   * </pre>
   */
  public static final class OrderGatewayServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<OrderGatewayServiceBlockingStub> {
    private OrderGatewayServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected OrderGatewayServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new OrderGatewayServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.exchange.gateway.dto.OrderProto.OrderResponse submitOrder(com.exchange.gateway.dto.OrderProto.OrderRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSubmitOrderMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.exchange.gateway.dto.OrderProto.CancelResponse cancelOrder(com.exchange.gateway.dto.OrderProto.CancelRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCancelOrderMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service OrderGatewayService.
   * <pre>
   * gRPC Protocol Buffers definitions replacing JSON for internal 
   * microservice-to-microservice communication
   * </pre>
   */
  public static final class OrderGatewayServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<OrderGatewayServiceFutureStub> {
    private OrderGatewayServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected OrderGatewayServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new OrderGatewayServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.exchange.gateway.dto.OrderProto.OrderResponse> submitOrder(
        com.exchange.gateway.dto.OrderProto.OrderRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSubmitOrderMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.exchange.gateway.dto.OrderProto.CancelResponse> cancelOrder(
        com.exchange.gateway.dto.OrderProto.CancelRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCancelOrderMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SUBMIT_ORDER = 0;
  private static final int METHODID_CANCEL_ORDER = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SUBMIT_ORDER:
          serviceImpl.submitOrder((com.exchange.gateway.dto.OrderProto.OrderRequest) request,
              (io.grpc.stub.StreamObserver<com.exchange.gateway.dto.OrderProto.OrderResponse>) responseObserver);
          break;
        case METHODID_CANCEL_ORDER:
          serviceImpl.cancelOrder((com.exchange.gateway.dto.OrderProto.CancelRequest) request,
              (io.grpc.stub.StreamObserver<com.exchange.gateway.dto.OrderProto.CancelResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSubmitOrderMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.exchange.gateway.dto.OrderProto.OrderRequest,
              com.exchange.gateway.dto.OrderProto.OrderResponse>(
                service, METHODID_SUBMIT_ORDER)))
        .addMethod(
          getCancelOrderMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.exchange.gateway.dto.OrderProto.CancelRequest,
              com.exchange.gateway.dto.OrderProto.CancelResponse>(
                service, METHODID_CANCEL_ORDER)))
        .build();
  }

  private static abstract class OrderGatewayServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    OrderGatewayServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.exchange.gateway.dto.OrderProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("OrderGatewayService");
    }
  }

  private static final class OrderGatewayServiceFileDescriptorSupplier
      extends OrderGatewayServiceBaseDescriptorSupplier {
    OrderGatewayServiceFileDescriptorSupplier() {}
  }

  private static final class OrderGatewayServiceMethodDescriptorSupplier
      extends OrderGatewayServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    OrderGatewayServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (OrderGatewayServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new OrderGatewayServiceFileDescriptorSupplier())
              .addMethod(getSubmitOrderMethod())
              .addMethod(getCancelOrderMethod())
              .build();
        }
      }
    }
    return result;
  }
}
