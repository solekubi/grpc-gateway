package com.esquel.gateway.controller;

import com.esquel.gateway.model.Result;
import com.esquel.gateway.service.GrpcProxyService;
import org.springframework.web.bind.annotation.*;

@RestController
public class GrpcController {

  private final GrpcProxyService grpcProxyService;

  public GrpcController(GrpcProxyService grpcProxyService) {
    this.grpcProxyService = grpcProxyService;
  }

  @RequestMapping(value = "/{rawFullMethodName}", method = RequestMethod.POST)
  public Result<Object> callService(@PathVariable String rawFullMethodName,
                                     @RequestBody String payload,
                                     @RequestParam(defaultValue = "{}") String headers) {
    return grpcProxyService.callService(rawFullMethodName,payload,headers);
  }
}
