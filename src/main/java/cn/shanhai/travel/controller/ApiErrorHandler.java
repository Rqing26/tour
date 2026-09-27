package cn.shanhai.travel.controller;

import cn.shanhai.travel.service.ApiException;
import java.util.Map;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> domain(ApiException e) {
    return response(e.status(), e.getMessage());
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<?> auth(AuthenticationException e) {
    return response(HttpStatus.UNAUTHORIZED, "用户名或密码不正确，或账号已停用");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> denied(AccessDeniedException e) {
    return response(HttpStatus.FORBIDDEN, "没有权限执行此操作");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    var f = e.getBindingResult().getFieldError();
    return response(
        HttpStatus.BAD_REQUEST,
        "请检查输入：" + (f == null ? "参数不完整" : f.getField() + " " + f.getDefaultMessage()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> malformed(Exception e) {
    return response(HttpStatus.BAD_REQUEST, "输入格式不正确，请检查必填项、日期和数值");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(DataIntegrityViolationException e) {
    return response(HttpStatus.CONFLICT, "记录重复或已被其他数据引用，请刷新后重试");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    log.error("Unhandled request failure", e);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时无法完成操作，请稍后重试");
  }

  private ResponseEntity<?> response(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("message", message));
  }
}
