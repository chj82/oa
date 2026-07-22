package com.oa.service.system.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.LoginSessionCache;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** 使用单机 Redis Lua 脚本原子维护登录会话及员工索引，不支持 Redis Cluster。 */
@Repository
public class StringRedisSessionStore {
  private static final DefaultRedisScript<Long> CREATE_SESSION_SCRIPT =
      script(
          "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]); "
              + "redis.call('SADD', KEYS[2], ARGV[3]); "
              + "redis.call('PEXPIRE', KEYS[2], ARGV[2]); return 1");
  private static final DefaultRedisScript<String> GET_AND_REFRESH_SCRIPT =
      new DefaultRedisScript<>(
          "local value = redis.call('GET', KEYS[1]); "
              + "if not value then return nil; end; "
              + "local session = cjson.decode(value); "
              + "redis.call('PEXPIRE', KEYS[1], ARGV[2]); "
              + "redis.call('PEXPIRE', ARGV[1] .. session.employeeId, ARGV[2]); return value",
          String.class);
  private static final DefaultRedisScript<Long> REMOVE_SESSION_SCRIPT =
      script(
          "redis.call('DEL', KEYS[1]); redis.call('SREM', KEYS[2], ARGV[1]); "
              + "redis.call('PEXPIRE', KEYS[2], ARGV[2]); return 1");
  private static final DefaultRedisScript<Long> INVALIDATE_EMPLOYEE_SCRIPT =
      script(
          "local members = redis.call('SMEMBERS', KEYS[1]); "
              + "for _, member in ipairs(members) do redis.call('DEL', ARGV[1] .. member); end; "
              + "redis.call('DEL', KEYS[1]); return #members");

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public StringRedisSessionStore(
      StringRedisTemplate redisTemplate,
      @Qualifier("loginSessionObjectMapper") ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  public void createSessionAtomically(
      String sessionKey,
      LoginSessionCache session,
      String indexKey,
      String tokenHash,
      Duration ttl) {
    execute(
        () ->
            redisTemplate.execute(
                CREATE_SESSION_SCRIPT,
                List.of(sessionKey, indexKey),
                writeSession(session),
                Long.toString(ttl.toMillis()),
                tokenHash));
  }

  public LoginSessionCache getAndRefreshAtomically(
      String sessionKey, String indexKeyPrefix, Duration ttl) {
    String value =
        execute(
            () ->
                redisTemplate.execute(
                    GET_AND_REFRESH_SCRIPT,
                    List.of(sessionKey),
                    indexKeyPrefix,
                    Long.toString(ttl.toMillis())));
    return readSession(value);
  }

  public void removeSessionAtomically(
      String sessionKey, String indexKey, String tokenHash, Duration ttl) {
    execute(
        () ->
            redisTemplate.execute(
                REMOVE_SESSION_SCRIPT,
                List.of(sessionKey, indexKey),
                tokenHash,
                Long.toString(ttl.toMillis())));
  }

  public void invalidateEmployeeSessionsAtomically(String indexKey, String sessionPrefix) {
    execute(
        () -> redisTemplate.execute(INVALIDATE_EMPLOYEE_SCRIPT, List.of(indexKey), sessionPrefix));
  }

  public LoginSessionCache get(String key) {
    String value = execute(() -> redisTemplate.opsForValue().get(key));
    return readSession(value);
  }

  private LoginSessionCache readSession(String value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.readValue(value, LoginSessionCache.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Redis登录会话数据格式无效", exception);
    }
  }

  public boolean hasSession(String key) {
    return Boolean.TRUE.equals(execute(() -> redisTemplate.hasKey(key)));
  }

  public Set<String> indexMembers(String key) {
    Set<String> members = execute(() -> redisTemplate.opsForSet().members(key));
    return members == null ? Set.of() : members;
  }

  public void removeFromIndex(String key, String tokenHash) {
    execute(() -> redisTemplate.opsForSet().remove(key, tokenHash));
  }

  private String writeSession(LoginSessionCache session) {
    try {
      return objectMapper.writeValueAsString(session);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Redis登录会话序列化失败", exception);
    }
  }

  private static DefaultRedisScript<Long> script(String source) {
    return new DefaultRedisScript<>(source, Long.class);
  }

  private <T> T execute(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException exception) {
      throw new AuthenticationInfrastructureException("认证服务暂不可用", exception);
    }
  }
}
