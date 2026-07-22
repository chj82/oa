package com.oa.common.exception;

/** 可转换为明确业务响应的异常。 */
public class BusinessException extends RuntimeException {
  /** 业务错误码。 */
  private final String code;

  /** 使用业务错误码和消息创建异常。 */
  public BusinessException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
