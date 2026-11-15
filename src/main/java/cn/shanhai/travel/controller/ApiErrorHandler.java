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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

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

  /** 容器层的大小拦截到不了服务层，必须在这里翻译，否则前端只能看到 500。 */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> tooLarge(MaxUploadSizeExceededException e) {
    return response(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 5MB");
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  ResponseEntity<?> missingPart(MissingServletRequestPartException e) {
    return response(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
  }

  /** 静态资源与不存在的路径走这里：没有这个映射会落到通用 500，把"文件不存在"说成"服务出错"。 */
  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<?> missingResource(NoResourceFoundException e) {
    return response(HttpStatus.NOT_FOUND, "请求的资源不存在");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(DataIntegrityViolationException e) {
    return response(HttpStatus.CONFLICT, "记录重复或已被其他数据引用，请刷新后重试");
  }

  /** 路径存在但方法不对：这是调用方用错了协议，不是服务端故障，不能落到通用 500。 */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<?> wrongMethod(HttpRequestMethodNotSupportedException e) {
    var supported = e.getSupportedHttpMethods();
    var hint = supported == null || supported.isEmpty() ? "" : "，请改用 " + supported;
    return response(
        HttpStatus.METHOD_NOT_ALLOWED, "该接口不支持 " + e.getMethod() + " 方法" + hint);
  }

  /**
   * 请求体格式不支持（例如把表单或 XML 打给只吃 JSON 的接口）。
   *
   * <p>它与 400 的区别是对外契约问题：400 表示"内容能读但不对"，415 表示"根本没按约定格式发"。
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<?> unsupportedType(HttpMediaTypeNotSupportedException e) {
    return response(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求格式不受支持，请使用 application/json");
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
