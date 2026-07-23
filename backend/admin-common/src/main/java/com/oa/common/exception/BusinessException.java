package com.oa.common.exception;

import com.oa.common.model.common.enums.ExceptionCode;

/** 可转换为明确业务响应的异常。 */
public class BusinessException extends RuntimeException {
  /** 统一异常码。 */
  private final ExceptionCode exceptionCode;

  /** 使用统一异常码的默认名称创建异常。 */
  public BusinessException(ExceptionCode exceptionCode) {
    this(exceptionCode, exceptionCode.getName());
  }

  /** 使用统一异常码和自定义消息创建异常。 */
  public BusinessException(ExceptionCode exceptionCode, String message) {
    super(message);
    this.exceptionCode = exceptionCode;
  }

  public ExceptionCode getExceptionCode() {
    return exceptionCode;
  }
}
