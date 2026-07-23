package com.oa.boot.advice;

import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.response.ApiResult;
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
  public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException exception) {
    return ResponseEntity.badRequest()
        .body(ApiResult.error(exception.getExceptionCode(), exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResult<Void>> handleValidation(
      MethodArgumentNotValidException exception) {
    return ResponseEntity.unprocessableEntity()
        .body(ApiResult.error(ExceptionCode.VALIDATION_FAILED));
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ApiResult<Void>> handleInfrastructure(DataAccessException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResult.error(ExceptionCode.INFRASTRUCTURE_UNAVAILABLE));
  }

  @ExceptionHandler(AuthenticationInfrastructureException.class)
  public ResponseEntity<ApiResult<Void>> handleAuthenticationInfrastructure(
      AuthenticationInfrastructureException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResult.error(ExceptionCode.AUTH_INFRASTRUCTURE_UNAVAILABLE));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception exception) {
    return ResponseEntity.internalServerError().body(ApiResult.error(ExceptionCode.INTERNAL_ERROR));
  }
}
