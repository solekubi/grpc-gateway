package com.esquel.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiDocument implements Serializable {

  @Builder.Default
  private String swagger = "2.0";

  private Map<String,Object> info;

  private String host;

  private String basePath;

  private List<Tag> tags;

  private List<String> schemes;

  private Map<String,Path> paths;

  private Map<String,Object> securityDefinitions;

  private Map<String,DefinitionType> definitions;

  private Map<String,Object> externalDocs;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class Tag {
    private String name;

    private String description;

    private Map<String,Object> externalDocs;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class  ParameterSchema {

    private String type;

    @JsonProperty("$ref")
    private String ref;
  }


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class DefinitionType {
    private String type;
    private String title;
    private Map<String, FieldProperty> properties;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class FieldProperty {
    private String type;
    private String format;
    @JsonProperty("$ref")
    private String ref;
    private Items items;
    @JsonProperty("enum")
    private List<String> enums;
    private FieldProperty additionalProperties;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class Items {
    private String type;
    private String format;
    @JsonProperty("$ref")
    private String ref;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class Path {
    private Operation get;
    private Operation post;
    private Operation put;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class Operation {
    private String description;
    private List<String> tags;
    private String operationId;
    private List<Parameter> parameters;
    private Map<String, ResponseObject> responses;

    @Builder.Default
    private List<String> schemes =  List.of("http","https");

    @Builder.Default
    private List<String> produces = Collections.singletonList("application/json");

    @Builder.Default
    private List<String> consumes = Collections.singletonList("application/json");
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class Parameter {
    private String name;

    @Builder.Default
    private String in = "body";

    private String description;

    @Builder.Default
    private Boolean required = true;

    private ParameterSchema schema;

    private String type;

    private String format;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class ResponseObject {
    private String code;
    private ParameterSchema schema;
    private String description;
  }

}
