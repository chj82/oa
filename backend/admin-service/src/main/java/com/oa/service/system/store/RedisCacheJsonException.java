package com.oa.service.system.store;

/** Redis 缓存 JSON 转换异常。 */
public class RedisCacheJsonException extends RuntimeException {

  public RedisCacheJsonException(String message, Throwable cause) {
    super(message, cause);
  }
}
