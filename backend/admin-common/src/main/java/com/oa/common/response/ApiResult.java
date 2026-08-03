package com.oa.common.response;

import com.oa.common.model.common.enums.ExceptionCode;

/** 统一接口响应。 */
public class ApiResult<T> {
  /** 是否成功。 */
  private boolean success;

  /** 响应码。 */
  private int code;

  /** 响应消息。 */
  private String message;

  /** 响应数据或错误详情。 */
  private T details;

  public ApiResult() {}

  /** 创建指定内容的响应。 */
  public ApiResult(int code, String message, T details) {
    this.success = code == ExceptionCode.SUCCESS.getCode();
    this.code = code;
    this.message = message;
    this.details = details;
  }

  /** 创建成功响应。 */
  public static <T> ApiResult<T> success(T details) {
    return new ApiResult<>(
        ExceptionCode.SUCCESS.getCode(), ExceptionCode.SUCCESS.getName(), details);
  }

  /** 使用统一异常码创建错误响应。 */
  public static ApiResult<Void> error(ExceptionCode exceptionCode) {
    return error(exceptionCode, exceptionCode.getName());
  }

  /** 使用统一异常码和自定义消息创建错误响应。 */
  public static ApiResult<Void> error(ExceptionCode exceptionCode, String message) {
    return new ApiResult<>(exceptionCode.getCode(), message, null);
  }

  public int getCode() {
    return code;
  }

  public boolean getSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public T getDetails() {
    return details;
  }

  public void setDetails(T details) {
    this.details = details;
  }
}
