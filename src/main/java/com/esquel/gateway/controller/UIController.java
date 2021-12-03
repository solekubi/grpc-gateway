package com.esquel.gateway.controller;

import com.esquel.gateway.model.ApiDocument;
import com.esquel.gateway.model.Endpoint;
import com.esquel.gateway.service.GrpcReflectionService;
import com.esquel.gateway.service.UIService;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicBoolean;

@Controller
public class UIController {

  private final UIService uiService;

  private final GrpcReflectionService grpcReflectionService;

  private final SimpMessagingTemplate simpMessagingTemplate;

  public UIController(UIService uiService, GrpcReflectionService grpcReflectionService,
                      SimpMessagingTemplate simpMessagingTemplate) {
    this.uiService = uiService;
    this.grpcReflectionService = grpcReflectionService;
    this.simpMessagingTemplate = simpMessagingTemplate;
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  public String index() {
    return "ui/index.html";
  }

  @RequestMapping(value = "/reset", method = RequestMethod.GET)
  @ResponseBody
  public void reset() {
    grpcReflectionService.loadGrpcServices();
    simpMessagingTemplate.convertAndSend("/topic/reload",true);
  }

  @RequestMapping(value = "/register", method = RequestMethod.PUT)
  @ResponseBody
  public void register(@RequestBody Endpoint endpoint) {
    grpcReflectionService.loadGrpcServicesByIpAndPort(endpoint);
    simpMessagingTemplate.convertAndSend("/topic/reload",true);
  }

  @RequestMapping(value = "/swagger-ui/api-docs", method = RequestMethod.GET)
  @ResponseBody
  public ApiDocument getApiDoc() {
    return uiService.getApiDoc();
  }

}
