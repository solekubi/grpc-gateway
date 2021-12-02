package com.esquel.gateway.handler;

import com.esquel.gateway.model.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

  @ExceptionHandler(value = Exception.class)
  public Result<Object> exceptionHandler(Exception e) {
    return Result.builder().code(400).message(e.getMessage()).build();
  }
}
