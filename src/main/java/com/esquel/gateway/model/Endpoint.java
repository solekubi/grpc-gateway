package com.esquel.gateway.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class Endpoint {

  @Value("${grpc.service.host}")
  private String host;

  @Value("${grpc.service.port}")
  private int port;

  public Endpoint(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public Endpoint() {
  }

  @Override
  public String toString() {
    return String.format("%s:%d", host, port);
  }
}
