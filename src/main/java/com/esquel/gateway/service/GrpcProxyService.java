package com.esquel.gateway.service;

import com.alibaba.fastjson.JSON;
import com.esquel.gateway.handler.MessageWriter;
import com.esquel.gateway.model.*;
import com.esquel.gateway.utils.ChannelFactory;
import com.esquel.gateway.utils.DynamicMessageMarshaller;
import com.esquel.gateway.utils.GrpcReflectionUtils;
import com.esquel.gateway.handler.CompositeStreamObserver;
import com.esquel.gateway.handler.DoneObserver;
import com.esquel.protobuf.ErrorInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.*;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.CallOptions.DEFAULT;
import static io.grpc.stub.ClientCalls.*;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Service("grpcProxyService")
public class GrpcProxyService {

  private static final Logger logger = LoggerFactory.getLogger(GrpcReflectionService.class);

  private final GrpcReflectionService grpcReflectionService;

  public GrpcProxyService(GrpcReflectionService grpcReflectionService) {
    this.grpcReflectionService = grpcReflectionService;
  }

  public CallResults invokeMethod(GrpcMethodDefinition definition,
                                  Channel channel,
                                  CallOptions callOptions,
                                  List<String> requestJsonTexts) {

    DescriptorProtos.FileDescriptorSet fileDescriptorSet = grpcReflectionService.getServiceByKey(definition.getFullServiceName());
    if (fileDescriptorSet == null) {
      return null;
    }
    ImmutableList<Descriptors.FileDescriptor> fileDescriptors = GrpcReflectionUtils.ListFileDescriptor(fileDescriptorSet);

    Descriptors.MethodDescriptor methodDescriptor = GrpcReflectionUtils.getServiceMethod(definition, fileDescriptors);

    ImmutableSet<Descriptors.Descriptor> listMessageTypes = GrpcReflectionUtils.listMessageTypes(fileDescriptors);

    JsonFormat.TypeRegistry registry = JsonFormat.TypeRegistry.newBuilder().add(listMessageTypes).build();

    List<DynamicMessage> requestMessages = GrpcReflectionUtils.parseToMessages(registry, methodDescriptor.getInputType(),
            requestJsonTexts);
    CallResults results = new CallResults();
    StreamObserver<DynamicMessage> streamObserver = MessageWriter.newInstance(registry, results);
    CallParams callParams = CallParams.builder()
            .methodDescriptor(methodDescriptor)
            .channel(channel)
            .callOptions(callOptions)
            .requests(requestMessages)
            .responseObserver(streamObserver)
            .build();
    try {
      Objects.requireNonNull(call(callParams)).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException("Caught exception while waiting for rpc", e);
    }
    return results;
  }

  @Nullable
  public ListenableFuture<Void> call(CallParams callParams) {
    checkParams(callParams);
    MethodDescriptor.MethodType methodType = GrpcReflectionUtils.fetchMethodType(callParams.getMethodDescriptor());
    List<DynamicMessage> requests = callParams.getRequests();
    StreamObserver<DynamicMessage> responseObserver = callParams.getResponseObserver();
    DoneObserver<DynamicMessage> doneObserver = new DoneObserver<>();
    StreamObserver<DynamicMessage> compositeObserver = CompositeStreamObserver.of(responseObserver, doneObserver);
    StreamObserver<DynamicMessage> requestObserver;
    switch (methodType) {
      case UNARY:
        asyncUnaryCall(createCall(callParams), requests.get(0), compositeObserver);
        return doneObserver.getCompletionFuture();
      case SERVER_STREAMING:
        asyncServerStreamingCall(createCall(callParams), requests.get(0), compositeObserver);
        return doneObserver.getCompletionFuture();
      case CLIENT_STREAMING:
        requestObserver = asyncClientStreamingCall(createCall(callParams), compositeObserver);
        requests.forEach(responseObserver::onNext);
        requestObserver.onCompleted();
        return doneObserver.getCompletionFuture();
      case BIDI_STREAMING:
        requestObserver = asyncBidiStreamingCall(createCall(callParams), compositeObserver);
        requests.forEach(responseObserver::onNext);
        requestObserver.onCompleted();
        return doneObserver.getCompletionFuture();
      default:
        logger.info("Unknown methodType:{}", methodType);
        return null;
    }
  }

  private void checkParams(CallParams callParams) {
    checkNotNull(callParams);
    checkNotNull(callParams.getMethodDescriptor());
    checkNotNull(callParams.getChannel());
    checkNotNull(callParams.getCallOptions());
    checkArgument(isNotEmpty(callParams.getRequests()));
    checkNotNull(callParams.getResponseObserver());
  }

  private ClientCall<DynamicMessage, DynamicMessage> createCall(CallParams callParams) {
    return callParams.getChannel().newCall(createGrpcMethodDescriptor(callParams.getMethodDescriptor()),
            callParams.getCallOptions());
  }

  private io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> createGrpcMethodDescriptor(Descriptors.MethodDescriptor descriptor) {
    return io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
            .setType(GrpcReflectionUtils.fetchMethodType(descriptor))
            .setFullMethodName(GrpcReflectionUtils.fetchFullMethodName(descriptor))
            .setRequestMarshaller(new DynamicMessageMarshaller(descriptor.getInputType()))
            .setResponseMarshaller(new DynamicMessageMarshaller(descriptor.getOutputType()))
            .build();
  }

  public Result<Object> callService(String rawFullMethodName, String payload, String headers) {
    Endpoint endpoint = getCurrentEndpoint();
    GrpcMethodDefinition methodDefinition = GrpcReflectionUtils.parseToMethodDefinition(rawFullMethodName);
    Map<String, Object> metaHeaderMap = JSON.parseObject(headers);
    ManagedChannel serviceChannel = null;
    try {
      serviceChannel = ChannelFactory.create(endpoint.getHost(), endpoint.getPort(), metaHeaderMap);
      CallResults results = invokeMethod(methodDefinition, serviceChannel, DEFAULT, singletonList(payload));
      return Result.builder().code(200).result(results.asJSON()).build();
    } catch (Exception e) {
      String message = e.toString();
      Metadata metadata = Status.trailersFromThrowable(e);
      if (Objects.nonNull(metadata)) {
        ErrorInfo errorInfo = metadata.get(ProtoUtils.keyForProto(ErrorInfo.getDefaultInstance()));
        if (Objects.nonNull(errorInfo)) {
          message = errorInfo.getMessage();
        }
      }
      throw new RuntimeException(message);
    } finally {
      if (serviceChannel != null) {
        serviceChannel.shutdown();
      }
    }
  }

  private Endpoint getCurrentEndpoint() {
    Endpoint endpoint = grpcReflectionService.getCurrentEndPoint();
    if (Objects.isNull(endpoint)) {
      grpcReflectionService.loadGrpcServices();
      endpoint = grpcReflectionService.getCurrentEndPoint();
    }
    return endpoint;
  }
}
