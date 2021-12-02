package com.esquel.gateway.service;

import com.esquel.gateway.store.MapStorage;
import com.esquel.gateway.utils.ChannelFactory;
import com.esquel.gateway.model.Endpoint;
import com.esquel.gateway.utils.GrpcReflectionUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.ManagedChannel;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Service("grpcReflectionService")
public class GrpcReflectionService {

  private static final Logger logger = LoggerFactory.getLogger(GrpcProxyService.class);

  private final Endpoint endpoint;

  private ManagedChannel channel;

  private final MapStorage<String, DescriptorProtos.FileDescriptorSet> FILE_DESCRIPTOR_SET_STORAGE = new MapStorage<>();

  private Endpoint currentEndpoint;

  public GrpcReflectionService(Endpoint endpoint) {
    this.endpoint = endpoint;
  }

  public void loadGrpcServices() {
    loadGrpcServicesByIpAndPort(this.endpoint);
  }

  @SneakyThrows
  public void loadGrpcServicesByIpAndPort(Endpoint endpoint) {
    try {
      if (Objects.isNull(this.channel) || !Objects.equals(endpoint, this.currentEndpoint)) {
        this.channel = ChannelFactory.create(endpoint.getHost(), endpoint.getPort());
      }
      List<String> serviceNames = GrpcReflectionUtils.listAllServices(this.channel).get();
      if (Objects.nonNull(serviceNames)) {
        FILE_DESCRIPTOR_SET_STORAGE.removeAll();
        for (String serviceName : serviceNames) {
          FILE_DESCRIPTOR_SET_STORAGE.add(serviceName, GrpcReflectionUtils.lookupService(this.channel, serviceName).get());
        }
      }
      this.currentEndpoint = endpoint;
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new RuntimeException("Can not load grpc services!");
    }
  }

  public DescriptorProtos.FileDescriptorSet getServiceByKey(String fullServiceName) {
    if (FILE_DESCRIPTOR_SET_STORAGE.isEmpty()) {
      loadGrpcServices();
    }
    return FILE_DESCRIPTOR_SET_STORAGE.get(fullServiceName);
  }


  public ImmutableMap<String, DescriptorProtos.FileDescriptorSet> getStorage() {
    return FILE_DESCRIPTOR_SET_STORAGE.getAll();
  }

  public Endpoint getCurrentEndPoint() {
    return this.currentEndpoint;
  }

  public ImmutableList<Descriptors.FileDescriptor> getFileDescriptorList() {

    ImmutableList.Builder<Descriptors.FileDescriptor> builder = ImmutableList.builder();

    FILE_DESCRIPTOR_SET_STORAGE.values().forEach(set -> {
      Map<String, Descriptors.FileDescriptor> descriptorCache = new HashMap<>();
      ImmutableMap<String, DescriptorProtos.FileDescriptorProto> descriptorProtoIndex = GrpcReflectionUtils.computeDescriptorProtoIndex(set);
      for (DescriptorProtos.FileDescriptorProto descriptorProto : set.getFileList()) {
        try {
          builder.add(GrpcReflectionUtils.descriptorFromProto(descriptorProto, descriptorProtoIndex, descriptorCache));
        } catch (Descriptors.DescriptorValidationException e) {
          logger.warn("Skipped descriptor " + descriptorProto.getName() + " due to error", e);
        }
      }
    });
    return builder.build();
  }

  public ImmutableList<Descriptors.ServiceDescriptor> getServiceDescriptorList() {
    String ignoreService = "grpc.reflection.v1alpha.ServerReflection";
    return ImmutableList.copyOf(getFileDescriptorList().stream().map(Descriptors.FileDescriptor::getServices).flatMap(Collection::stream)
            .filter(s -> !ignoreService.equalsIgnoreCase(s.getFullName()))
            .sorted(Comparator.comparing(Descriptors.ServiceDescriptor::getFullName))
            .collect(Collectors.toList()));
  }

  public ImmutableList<Descriptors.MethodDescriptor> getMethodDescriptorList() {
    return ImmutableList.copyOf(getServiceDescriptorList().stream().map(Descriptors.ServiceDescriptor::getMethods)
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));
  }
}
