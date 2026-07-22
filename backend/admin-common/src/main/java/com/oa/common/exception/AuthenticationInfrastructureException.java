package com.oa.common.exception;

/** Redis 登录认证基础设施不可用异常。 */
public class AuthenticationInfrastructureException extends RuntimeException {
  public AuthenticationInfrastructureException(String message) {
    super(message);
  }

  public AuthenticationInfrastructureException(String message, Throwable cause) {
    super(message, cause);
  }
}
