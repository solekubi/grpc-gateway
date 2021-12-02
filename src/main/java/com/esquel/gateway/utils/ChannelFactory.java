package com.esquel.gateway.utils;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.Map;


import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static java.util.Collections.emptyMap;

public class ChannelFactory {

  public static ManagedChannel create(String host, int port) {
    return create(host, port, emptyMap());
  }

  public static <V, K> ManagedChannel create(String host, int port, Map<String, Object> metaDataMap) {
    return NettyChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .intercept(metadataInterceptor(metaDataMap))
            .enableRetry()
            .build();
  }

  private static ClientInterceptor metadataInterceptor(Map<String, Object> metaDataMap) {
    return new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
              final MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, final Channel next) {

        return new ClientInterceptors.CheckedForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          protected void checkedStart(Listener<RespT> responseListener, Metadata headers) {
            metaDataMap.forEach((k, v) -> {
              Metadata.Key<String> mKey = Metadata.Key.of(k, ASCII_STRING_MARSHALLER);
              headers.put(mKey, String.valueOf(v));
            });
            delegate().start(responseListener, headers);
          }
        };
      }
    };
  }
}
