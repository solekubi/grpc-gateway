package com.esquel.gateway.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Endpoint endpoint = (Endpoint) o;
    return port == endpoint.port && Objects.equals(host, endpoint.host);
  }

  @Override
  public int hashCode() {
    return Objects.hash(host, port);
  }
}
