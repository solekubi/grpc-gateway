package com.esquel.gateway.controller;

import com.esquel.gateway.model.ApiDocument;
import com.esquel.gateway.model.Endpoint;
import com.esquel.gateway.service.GrpcReflectionService;
import com.esquel.gateway.service.UIService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class UIController {

  private final UIService uiService;

  private final GrpcReflectionService grpcReflectionService;

  public UIController(UIService uiService, GrpcReflectionService grpcReflectionService) {
    this.uiService = uiService;
    this.grpcReflectionService = grpcReflectionService;
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  public String index() {
    return "ui/index.html";
  }

  @RequestMapping(value = "/reset", method = RequestMethod.GET)
  public String reloadService() {
    grpcReflectionService.loadGrpcServices();
    return "redirect:/";
  }

  @RequestMapping(value = "/register", method = RequestMethod.PUT)
  public String reloadServiceByHost(@RequestBody Endpoint endpoint) {
    grpcReflectionService.loadGrpcServicesByIpAndPort(endpoint);
    return "redirect:/";
  }

  @RequestMapping(value = "/api-docs", method = RequestMethod.GET)
  @ResponseBody
  public ApiDocument getApiDoc() {
    return uiService.getApiDoc();
  }
}
