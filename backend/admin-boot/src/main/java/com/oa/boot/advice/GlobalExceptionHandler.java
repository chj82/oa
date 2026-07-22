package com.oa.boot.advice;

import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.exception.BusinessException;
import com.oa.common.response.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 接口统一异常响应处理。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(exception.getCode(), exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(
      MethodArgumentNotValidException exception) {
    return ResponseEntity.unprocessableEntity()
        .body(ApiResponse.error("VALIDATION_FAILED", "请求字段校验失败"));
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ApiResponse<Void>> handleInfrastructure(DataAccessException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error("INFRASTRUCTURE_UNAVAILABLE", "基础设施暂不可用"));
  }

  @ExceptionHandler(AuthenticationInfrastructureException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthenticationInfrastructure(
      AuthenticationInfrastructureException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error("AUTH_INFRASTRUCTURE_UNAVAILABLE", "认证服务暂不可用"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
    return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_ERROR", "系统异常"));
  }
}
