package com.esquel.gateway.utils;

import com.esquel.gateway.handler.ListServicesHandler;
import com.esquel.gateway.handler.LookupServiceHandler;
import com.esquel.gateway.model.GrpcMethodDefinition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class GrpcReflectionUtils {

  private static final Logger logger = LoggerFactory.getLogger(GrpcReflectionUtils.class);

  private static final long LIST_RPC_DEADLINE_MS = 10_000;

  private static final long LOOKUP_RPC_DEADLINE_MS = 10_000;

  public static GrpcMethodDefinition parseToMethodDefinition(String rawMethodName) {
    checkArgument(isNotBlank(rawMethodName), "Raw method name can't be empty.");
    int methodSplitPosition = rawMethodName.lastIndexOf(".");
    checkArgument(methodSplitPosition != -1, "No package name and service name found.");
    String methodName = rawMethodName.substring(methodSplitPosition + 1);
    checkArgument(isNotBlank(methodName), "Method name can't be empty.");
    String fullServiceName = rawMethodName.substring(0, methodSplitPosition);
    int serviceSplitPosition = fullServiceName.lastIndexOf(".");
    String serviceName = fullServiceName.substring(serviceSplitPosition + 1);
    String packageName = "";
    if (serviceSplitPosition != -1) {
      packageName = fullServiceName.substring(0, serviceSplitPosition);
    }
    checkArgument(isNotBlank(serviceName), "Service name can't be empty.");
    return new GrpcMethodDefinition(packageName, serviceName, methodName);
  }

  public static List<DynamicMessage> parseToMessages(JsonFormat.TypeRegistry registry, Descriptors.Descriptor descriptor,
                                              List<String> jsonTexts) {
    JsonFormat.Parser parser = JsonFormat.parser().usingTypeRegistry(registry);
    List<DynamicMessage> messages = new ArrayList<>();
    try {
      for (String jsonText : jsonTexts) {
        DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(descriptor);
        parser.merge(jsonText, messageBuilder);
        messages.add(messageBuilder.build());
      }
      return messages;
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException("Unable to parse json text", e);
    }
  }

  public static MethodDescriptor.MethodType fetchMethodType(Descriptors.MethodDescriptor methodDescriptor) {
    boolean clientStreaming = methodDescriptor.toProto().getClientStreaming();
    boolean serverStreaming = methodDescriptor.toProto().getServerStreaming();
    if (clientStreaming && serverStreaming) {
      return MethodDescriptor.MethodType.BIDI_STREAMING;
    } else if (!clientStreaming && !serverStreaming) {
      return MethodDescriptor.MethodType.UNARY;
    } else if (!clientStreaming) {
      return MethodDescriptor.MethodType.SERVER_STREAMING;
    } else {
      return MethodDescriptor.MethodType.SERVER_STREAMING;
    }
  }

  public static String fetchFullMethodName(Descriptors.MethodDescriptor methodDescriptor) {
    String serviceName = methodDescriptor.getService().getFullName();
    String methodName = methodDescriptor.getName();
    return generateFullMethodName(serviceName, methodName);
  }

  public static ListenableFuture<ImmutableList<String>> listAllServices(Channel channel) {
    ListServicesHandler listServicesHandler = new ListServicesHandler();
    StreamObserver<ServerReflectionRequest> requestStream = ServerReflectionGrpc.newStub(channel)
            .withDeadlineAfter(LIST_RPC_DEADLINE_MS, TimeUnit.SECONDS)
            .serverReflectionInfo(listServicesHandler);
    return listServicesHandler.start(requestStream);
  }

  public static ListenableFuture<DescriptorProtos.FileDescriptorSet> lookupService(Channel channel, String serviceName) {
    LookupServiceHandler lookupServiceHandler = new LookupServiceHandler(serviceName);
    StreamObserver<ServerReflectionRequest> requestStream = ServerReflectionGrpc.newStub(channel)
            .withDeadlineAfter(LOOKUP_RPC_DEADLINE_MS, TimeUnit.SECONDS)
            .serverReflectionInfo(lookupServiceHandler);
    return lookupServiceHandler.start(requestStream);
  }


  public static ImmutableList<Descriptors.FileDescriptor> ListFileDescriptor(DescriptorProtos.FileDescriptorSet descriptorSet) {
    ImmutableMap<String, DescriptorProtos.FileDescriptorProto> descriptorProtoIndex =
            computeDescriptorProtoIndex(descriptorSet);
    Map<String, Descriptors.FileDescriptor> descriptorCache = new HashMap<>();

    ImmutableList.Builder<Descriptors.FileDescriptor> result = ImmutableList.builder();
    for (DescriptorProtos.FileDescriptorProto descriptorProto : descriptorSet.getFileList()) {
      try {
        result.add(descriptorFromProto(descriptorProto, descriptorProtoIndex, descriptorCache));
      } catch (Descriptors.DescriptorValidationException e) {
        logger.warn("Skipped descriptor " + descriptorProto.getName() + " due to error", e);
      }
    }
    return result.build();
  }

  public  static Descriptors.MethodDescriptor getServiceMethod(GrpcMethodDefinition definition,
                                                               ImmutableList<Descriptors.FileDescriptor> fileDescriptors) {
    Descriptors.ServiceDescriptor service = findService(definition.getPackageName(), definition.getServiceName(),fileDescriptors);
    Descriptors.MethodDescriptor method = service.findMethodByName(definition.getMethodName());
    if (method == null) {
      throw new IllegalArgumentException(
              "Unable to find method " + definition.getMethodName()
                      + " in service " + definition.getServiceName());
    }
    return method;
  }

  public static ImmutableSet<Descriptors.Descriptor> listMessageTypes(ImmutableList<Descriptors.FileDescriptor> fileDescriptors) {
    ImmutableSet.Builder<Descriptors.Descriptor> resultBuilder = ImmutableSet.builder();
    fileDescriptors.forEach(d -> resultBuilder.addAll(d.getMessageTypes()));
    return resultBuilder.build();
  }

  public static Descriptors.ServiceDescriptor findService(String packageName, String serviceName,
                                                    ImmutableList<Descriptors.FileDescriptor> fileDescriptors) {
    for (Descriptors.FileDescriptor fileDescriptor : fileDescriptors) {
      if (!fileDescriptor.getPackage().equals(packageName)) {
        continue;
      }
      Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName(serviceName);
      if (serviceDescriptor != null) {
        return serviceDescriptor;
      }
    }
    throw new IllegalArgumentException("Unable to find service with name: " + serviceName);
  }

  /**
   * Returns a map from descriptor proto name as found inside the descriptors to protos.
   */
  public static ImmutableMap<String, DescriptorProtos.FileDescriptorProto> computeDescriptorProtoIndex(
          DescriptorProtos.FileDescriptorSet fileDescriptorSet) {
    ImmutableMap.Builder<String, DescriptorProtos.FileDescriptorProto> resultBuilder = ImmutableMap.builder();
    for (DescriptorProtos.FileDescriptorProto descriptorProto : fileDescriptorSet.getFileList()) {
      resultBuilder.put(descriptorProto.getName(), descriptorProto);
    }
    return resultBuilder.build();
  }

  /**
   * Recursively constructs file descriptors for all dependencies of the supplied proto and returns
   * a {@link Descriptors.FileDescriptor} for the supplied proto itself. For maximal efficiency, reuse the
   * descriptorCache argument across calls.
   */
  public static Descriptors.FileDescriptor descriptorFromProto(
          DescriptorProtos.FileDescriptorProto descriptorProto,
          ImmutableMap<String, DescriptorProtos.FileDescriptorProto> descriptorProtoIndex,
          Map<String, Descriptors.FileDescriptor> descriptorCache) throws Descriptors.DescriptorValidationException {
    // First, check the cache.
    String descriptorName = descriptorProto.getName();
    if (descriptorCache.containsKey(descriptorName)) {
      return descriptorCache.get(descriptorName);
    }

    // Then, fetch all the required dependencies recursively.
    ImmutableList.Builder<Descriptors.FileDescriptor> dependencies = ImmutableList.builder();
    for (String dependencyName : descriptorProto.getDependencyList()) {
      if (!descriptorProtoIndex.containsKey(dependencyName)) {
        throw new IllegalArgumentException("Could not find dependency: " + dependencyName);
      }
      DescriptorProtos.FileDescriptorProto dependencyProto = descriptorProtoIndex.get(dependencyName);
      dependencies.add(descriptorFromProto(dependencyProto, descriptorProtoIndex, descriptorCache));
    }

    // Finally, construct the actual descriptor.
    Descriptors.FileDescriptor[] empty = new Descriptors.FileDescriptor[0];
    return Descriptors.FileDescriptor.buildFrom(descriptorProto, dependencies.build().toArray(empty));
  }
}
