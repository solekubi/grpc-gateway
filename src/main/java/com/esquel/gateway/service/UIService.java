package com.esquel.gateway.service;

import com.esquel.gateway.constrants.FieldTypeEnum;
import com.esquel.gateway.model.ApiDocument;
import com.esquel.gateway.model.Endpoint;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Service("uIService")
public class UIService {

  private static final Logger logger = LoggerFactory.getLogger(UIService.class);

  public static final String DEFINITION_REF_PREFIX = "#/definitions/";

  public static final String DEFAULT_TAG = "grpc.GateWay";

  private final GrpcReflectionService grpcReflectionService;

  @Value("${server.port}")
  private int port;

  private final Endpoint endpoint;

  public UIService(GrpcReflectionService grpcReflectionService, Endpoint endpoint) {
    this.grpcReflectionService = grpcReflectionService;
    this.endpoint = endpoint;
  }

  public ApiDocument getApiDoc() {

    if (grpcReflectionService.getStorage().isEmpty()) {
      try {
        grpcReflectionService.loadGrpcServices();
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }

    ApiDocument.ApiDocumentBuilder builder = ApiDocument.builder();

    // set info
    builder.info(new HashMap<>() {
      {
        put("title", "Grpc GateWay Swagger");
        put("description", String.format("Default Grpc Endpoint Register = [%s]", endpoint.toString()));
        put("version", "1.0.0");
      }
    });

    try {
      String server_host = System.getenv("UI_SERVER_HOST");
      String server_port = System.getenv("UI_SERVER_PORT");
      if (Objects.nonNull(server_host) && !server_host.isEmpty() &&
              Objects.nonNull(server_port) && !server_port.isEmpty()) {
        builder.host(String.format("%s:%s", server_host, server_port));
      } else {
        builder.host(String.format("%s:%d", java.net.InetAddress.getLocalHost().getHostName(), port));
      }
    } catch (Exception e) {
      builder.host(String.format("%s:%d", "localhost", port));
    }

    //tags
    Endpoint currentEndPoint = grpcReflectionService.getCurrentEndPoint();

    List<ApiDocument.Tag> tagList = new ArrayList<>();

    tagList.add(0, ApiDocument.Tag.builder().name(DEFAULT_TAG).externalDocs(new HashMap<>() {
      {
        put("description", "Endpoint Register");
        put("url", Objects.isNull(currentEndPoint) ? "not found" : currentEndPoint.toString());
      }
    }).build());

    ImmutableList<Descriptors.ServiceDescriptor> serviceDescriptorList = ImmutableList.<Descriptors.ServiceDescriptor>builder().build();

    try {

      serviceDescriptorList = grpcReflectionService.getServiceDescriptorList();

      List<ApiDocument.Tag> serviceTags = serviceDescriptorList.stream().map(s -> ApiDocument.Tag.builder().name(s.getFullName()).build()).collect(Collectors.toList());

      tagList.addAll(serviceTags);

    } catch (Exception e) {
      logger.error(e.getMessage());
    }

    builder.tags(tagList);

    builder.schemes(List.of("http", "https"));

    builder.securityDefinitions(new HashMap<>() {
      {
        put("api_key", new HashMap<>() {
          {
            put("type", "apiKey");
            put("name", "Authorization ");
            put("in", "header");
          }
        });
      }
    });

    //获取实体类
    LinkedHashMap<String, ApiDocument.DefinitionType> definitionMap = new LinkedHashMap<>();

    //新增reload
    definitionMap.put(DEFAULT_TAG + ".Endpoint", ApiDocument.DefinitionType.builder()
            .title("Endpoint")
            .type(FieldTypeEnum.OBJECT.getType())
            .properties(new HashMap<>() {
              {
                put("host", ApiDocument.FieldProperty.builder().type(FieldTypeEnum.STRING.getType()).build());
                put("port", ApiDocument.FieldProperty.builder().type(FieldTypeEnum.INT32.getType()).build());
              }
            })
            .build());

    List<Descriptors.FileDescriptor> fileDescriptorsList = grpcReflectionService.getFileDescriptorList();

    // build definitions
    for (Descriptors.FileDescriptor dfp : fileDescriptorsList) {
      String packageName = dfp.getPackage();
      List<Descriptors.Descriptor> messageTypes = dfp.getMessageTypes();
      parseDefinitionType(packageName, messageTypes, definitionMap);
    }


    builder.definitions(definitionMap.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                    (o, n) -> o, LinkedHashMap::new)));

    Map<String, ApiDocument.Path> pathMap = new HashMap<>();

    pathMap.put("/reset", ApiDocument.Path.builder()
            .get(ApiDocument.Operation.builder().tags(List.of(DEFAULT_TAG))
                    .responses(new HashMap<>() {
                      {
                        put(String.valueOf(HttpStatus.OK.value()), ApiDocument.ResponseObject.builder()
                                .schema(ApiDocument.ParameterSchema.builder().type(FieldTypeEnum.OBJECT.getType()).build())
                                .build());
                      }
                    })
                    .operationId("resetService").description("Reset Service Info").build()).build());

    pathMap.put("/register", ApiDocument.Path.builder()
            .put(ApiDocument.Operation.builder().tags(List.of(DEFAULT_TAG))
                    .parameters(List.of(ApiDocument.Parameter.builder().name("Endpoint")
                            .schema(ApiDocument.ParameterSchema.builder().ref(
                                    DEFINITION_REF_PREFIX + DEFAULT_TAG + ".Endpoint"
                            ).build()).build()))
                    .responses(new HashMap<>() {
                      {
                        put(String.valueOf(HttpStatus.OK.value()), ApiDocument.ResponseObject.builder()
                                .schema(ApiDocument.ParameterSchema.builder().type(FieldTypeEnum.OBJECT.getType()).build())
                                .build());
                      }
                    })
                    .operationId("registerService").description("Register New  Service InfoBy Port And Id").build())
            .build());


    // build paths
    for (Descriptors.ServiceDescriptor sp : serviceDescriptorList) {
      sp.getMethods().forEach(m -> {
        ApiDocument.Path.PathBuilder pathBuilder = ApiDocument.Path.builder();
        ApiDocument.Operation operation = parseOperation(m);
        operation.setTags(List.of(sp.getFullName()));
        pathBuilder.post(operation);
        pathMap.put(String.format("/%s", m.getFullName()), pathBuilder.build());
      });
    }

    builder.paths(pathMap);

    return builder.build();
  }

  private ApiDocument.Operation parseOperation(Descriptors.MethodDescriptor method) {
    ApiDocument.Operation.OperationBuilder builder = ApiDocument.Operation.builder();
    Descriptors.Descriptor inputType = method.getInputType();
    Descriptors.Descriptor outputType = method.getOutputType();
    builder.description(method.getName());
    builder.operationId(method.getFullName());
    List<ApiDocument.Parameter> parameters = parseParameters(inputType);
    parameters.add(buildHeaderParameter());
    builder.parameters(parameters);
    Map<String, ApiDocument.ResponseObject> response = parseResponse(outputType);
    builder.responses(response);
    return builder.build();
  }

  private Map<String, ApiDocument.ResponseObject> parseResponse(Descriptors.Descriptor outputType) {
    ApiDocument.ResponseObject.ResponseObjectBuilder builder = ApiDocument.ResponseObject.builder();
    ApiDocument.ParameterSchema.ParameterSchemaBuilder schemaBuilder = ApiDocument.ParameterSchema.builder();
    schemaBuilder.ref(DEFINITION_REF_PREFIX + outputType.getFullName());
    schemaBuilder.type(FieldTypeEnum.OBJECT.getType());
    builder.schema(schemaBuilder.build());
    Map<String, ApiDocument.ResponseObject> response = new HashMap<>();
    response.put(String.valueOf(HttpStatus.OK.value()), builder.build());
    return response;
  }

  private ApiDocument.Parameter buildHeaderParameter() {
    ApiDocument.Parameter.ParameterBuilder builder = ApiDocument.Parameter.builder();
    builder.name("headers");
    builder.description("Headers passed to gRPC server");
    builder.in("query");
    builder.type("object");
    builder.required(false);
    return builder.build();
  }

  private List<ApiDocument.Parameter> parseParameters(Descriptors.Descriptor inputType) {
    List<ApiDocument.Parameter> parameters = new ArrayList<>();
    ApiDocument.Parameter.ParameterBuilder builder = ApiDocument.Parameter.builder();
    builder.name(inputType.getName());
    ApiDocument.ParameterSchema.ParameterSchemaBuilder schemaBuilder = ApiDocument.ParameterSchema.builder();
    schemaBuilder.ref(DEFINITION_REF_PREFIX + inputType.getFullName());
    builder.schema(schemaBuilder.build());
    parameters.add(builder.build());
    return parameters;
  }

  private void parseDefinitionType(String prefix, List<Descriptors.Descriptor> messageTypeList,
                                   HashMap<String, ApiDocument.DefinitionType> definitionMap) {
    if (Objects.isNull(definitionMap)) return;

    for (Descriptors.Descriptor dp : messageTypeList) {
      ApiDocument.DefinitionType.DefinitionTypeBuilder definitionTypeBuilder = ApiDocument.DefinitionType.builder();
      definitionTypeBuilder.title(dp.getName());
      definitionTypeBuilder.type(FieldTypeEnum.OBJECT.getType());
      List<Descriptors.FieldDescriptor> fields = dp.getFields();
      Map<String, ApiDocument.FieldProperty> propertyMap = new HashMap<>();
      for (Descriptors.FieldDescriptor fd : fields) {
        String fullName = null;
        if (fd.getType().getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
          Descriptors.Descriptor messageType = fd.getMessageType();
          fullName = Objects.nonNull(messageType) ? messageType.getFullName() : null;
        }
        ApiDocument.FieldProperty fieldProperty = parseFieldProperty(fd, fullName);
        propertyMap.put(fd.getName(), fieldProperty);
      }
      definitionTypeBuilder.properties(propertyMap);
      String key = String.format("%s.%s", prefix, dp.getName());
      definitionMap.put(key, definitionTypeBuilder.build());

      List<Descriptors.Descriptor> nestedTypeList = dp.getNestedTypes();

      if (!nestedTypeList.isEmpty()) {
        parseDefinitionType(key, nestedTypeList, definitionMap);
      }
    }
  }

  private ApiDocument.FieldProperty parseFieldProperty(Descriptors.FieldDescriptor fieldDescriptor, String fullName) {
    Descriptors.FieldDescriptor.Type type = fieldDescriptor.getType();

    FieldTypeEnum fieldTypeEnum = FieldTypeEnum.getByFieldType(type);

    ApiDocument.FieldProperty fieldProperty = new ApiDocument.FieldProperty();
    if (fieldDescriptor.isRepeated()) {
      // map
      if (type == Descriptors.FieldDescriptor.Type.MESSAGE
              && fieldDescriptor.getMessageType().getOptions().getMapEntry()) {
        fieldProperty.setType(FieldTypeEnum.OBJECT.getType());
        Descriptors.Descriptor messageType = fieldDescriptor.getMessageType();
        Descriptors.FieldDescriptor mapValueType = messageType.getFields().get(1);
        fieldProperty.setAdditionalProperties(parseFieldProperty(mapValueType, fullName));
      } else { // array
        fieldProperty.setType(FieldTypeEnum.ARRAY.getType());
        ApiDocument.Items items = new ApiDocument.Items();
        items.setType(fieldTypeEnum.getType());
        items.setFormat(fieldTypeEnum.getFormat());
        if (fieldTypeEnum == FieldTypeEnum.OBJECT && Objects.nonNull(fullName)) {
          items.setRef(DEFINITION_REF_PREFIX + fullName);
        }
        fieldProperty.setItems(items);
      }
    }
    // object reference
    else if (fieldTypeEnum == FieldTypeEnum.OBJECT && Objects.nonNull(fullName)) {
      fieldProperty.setRef(DEFINITION_REF_PREFIX + fullName);
    }
    // enum
    else if (fieldTypeEnum == FieldTypeEnum.ENUM) {
      fieldProperty.setType(FieldTypeEnum.ENUM.getType());
      List<String> enums = new ArrayList<>();
      Descriptors.EnumDescriptor enumDescriptor = fieldDescriptor.getEnumType();
      enumDescriptor.getValues().forEach(enumValueDescriptor -> enums.add(enumValueDescriptor.getName()));
      fieldProperty.setEnums(enums);
    }
    // other simple types
    else {
      fieldProperty.setType(fieldTypeEnum.getType());
      fieldProperty.setFormat(fieldTypeEnum.getFormat());
    }
    return fieldProperty;
  }
}
