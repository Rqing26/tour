package cn.shanhai.travel.service;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public static ApiException missing(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }

  public static ApiException forbidden() {
    return forbidden("没有权限执行此操作");
  }

  /** 带原因的拒绝：让人知道是哪一条规则不满足，而不是笼统的"没有权限"。 */
  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, message);
  }

  public static ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  /** 请求体超过业务上限：与"格式不合法"区分开，前端可据此提示"图片太大"而不是"格式不对"。 */
  public static ApiException tooLarge(String message) {
    return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, message);
  }
}
