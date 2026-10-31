package com.assurance.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorAdvice {
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("code","invalid_request","message","请求参数或配置无效"));}
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException error){return ResponseEntity.status(error.getStatusCode()).body(Map.of("code","invalid_state","message",error.getReason()==null?"操作不可用":error.getReason()));}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unavailable(){return ResponseEntity.status(503).body(Map.of("code","service_error","message","服务暂不可用，请检查服务端运行状态"));}
}
