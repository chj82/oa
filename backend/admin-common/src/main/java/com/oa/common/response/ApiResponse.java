package com.oa.common.response;

/** 统一接口响应。 */
public class ApiResponse<T> {
  /** 响应码。 */
  private String code;

  /** 响应消息。 */
  private String message;

  /** 响应数据或错误详情。 */
  private T details;

  public ApiResponse() {}

  /** 创建指定内容的响应。 */
  public ApiResponse(String code, String message, T details) {
    this.code = code;
    this.message = message;
    this.details = details;
  }

  /** 创建成功响应。 */
  public static <T> ApiResponse<T> success(T details) {
    return new ApiResponse<>("SUCCESS", "成功", details);
  }

  /** 创建错误响应。 */
  public static ApiResponse<Void> error(String code, String message) {
    return new ApiResponse<>(code, message, null);
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
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
