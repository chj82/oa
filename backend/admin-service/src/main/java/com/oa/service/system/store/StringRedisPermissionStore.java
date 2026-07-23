package com.oa.service.system.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeePermissionCache;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** 使用 Redis 全局版本控制员工接口权限缓存。 */
@Repository
public class StringRedisPermissionStore {
  private static final String PERMISSION_PREFIX = "admin:permission:";
  private static final String VERSION_KEY = "admin:permission:version";
  private static final String INVALIDATING_KEY = "admin:permission:invalidating";
  private static final String REBUILD_LOCK_PREFIX = "admin:permission:rebuild-lock:";
  private static final String CACHE_MISS = "__PERMISSION_CACHE_MISS__";
  private static final String CACHE_INVALIDATING = "__PERMISSION_INVALIDATING__";
  private static final String CACHE_INVALID = "__PERMISSION_CACHE_INVALID__";
  private static final Duration CACHE_TTL = Duration.ofDays(1);
  private static final Duration REBUILD_LOCK_TTL = Duration.ofSeconds(10);
  private static final DefaultRedisScript<String> GET_IF_CURRENT_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('EXISTS', KEYS[3]) == 1 then return '"
              + CACHE_INVALIDATING
              + "'; end; "
              + "local version = redis.call('GET', KEYS[1]); "
              + "if not version then redis.call('SET', KEYS[1], '0'); version = '0'; end; "
              + "local value = redis.call('GET', KEYS[2]); "
              + "if not value then return '"
              + CACHE_MISS
              + "'; end; "
              + "local cache = cjson.decode(value); "
              + "if type(cache.version) ~= 'number' or type(cache.apiPaths) ~= 'table' "
              + "then return '"
              + CACHE_INVALID
              + "'; end; "
              + "if cache.version < 0 then return '"
              + CACHE_INVALID
              + "'; end; "
              + "if cache.version ~= tonumber(version) then return '"
              + CACHE_MISS
              + "'; end; "
              + "return value",
          String.class);
  private static final DefaultRedisScript<Long> CURRENT_VERSION_SCRIPT =
      new DefaultRedisScript<>(
          "local version = redis.call('GET', KEYS[1]); "
              + "if not version then redis.call('SET', KEYS[1], '0'); return 0; end; "
              + "return tonumber(version)",
          Long.class);
  private static final DefaultRedisScript<String> BEGIN_INVALIDATION_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('EXISTS', KEYS[2]) == 1 then redis.call('INCR', KEYS[1]); end; "
              + "redis.call('SET', KEYS[2], ARGV[1]); return ARGV[1]",
          String.class);
  private static final DefaultRedisScript<Long> COMPLETE_INVALIDATION_SCRIPT =
      script(
          "local version = redis.call('INCR', KEYS[1]); "
              + "if redis.call('GET', KEYS[2]) == ARGV[1] then redis.call('DEL', KEYS[2]); end; "
              + "return version");
  private static final DefaultRedisScript<Long> CANCEL_INVALIDATION_SCRIPT =
      script(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); "
              + "end; return 0");
  private static final DefaultRedisScript<Long> RETRY_INVALIDATION_SCRIPT =
      script(
          "if redis.call('EXISTS', KEYS[2]) == 1 then "
              + "local version = redis.call('INCR', KEYS[1]); redis.call('DEL', KEYS[2]); "
              + "return version; end; "
              + "local version = redis.call('GET', KEYS[1]); "
              + "if not version then redis.call('SET', KEYS[1], '0'); return 0; end; "
              + "return tonumber(version)");
  private static final DefaultRedisScript<Long> RELEASE_REBUILD_LOCK_SCRIPT =
      script(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); "
              + "end; return 0");

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public StringRedisPermissionStore(
      StringRedisTemplate redisTemplate,
      @Qualifier("loginSessionObjectMapper") ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  /** 单次 Lua 读取全局版本和员工缓存，仅返回版本匹配的缓存。 */
  public EmployeePermissionCache getIfCurrent(long employeeId) {
    String value =
        executeRequired(
            () ->
                redisTemplate.execute(
                    GET_IF_CURRENT_SCRIPT,
                    List.of(VERSION_KEY, PERMISSION_PREFIX + employeeId, INVALIDATING_KEY)));
    if (CACHE_MISS.equals(value)) {
      return null;
    }
    if (CACHE_INVALIDATING.equals(value) || CACHE_INVALID.equals(value)) {
      throw unavailable();
    }
    return readCache(value);
  }

  /** 原子读取全局权限版本，不存在时初始化为零。 */
  public long currentVersion() {
    return validVersion(
        executeRequired(() -> redisTemplate.execute(CURRENT_VERSION_SCRIPT, List.of(VERSION_KEY))));
  }

  /** 将员工权限缓存写入 Redis 并设置一天有效期。 */
  public void put(long employeeId, EmployeePermissionCache cache) {
    validateCache(cache);
    executeRequired(
        () -> {
          redisTemplate
              .opsForValue()
              .set(PERMISSION_PREFIX + employeeId, writeCache(cache), CACHE_TTL);
          return Boolean.TRUE;
        });
  }

  /** 原子递增全局权限版本，使全部员工旧缓存失效。 */
  public long incrementVersion() {
    return validVersion(executeRequired(() -> redisTemplate.opsForValue().increment(VERSION_KEY)));
  }

  /** 立即标记权限失效处理中；若存在崩溃残留，先递增版本使旧缓存失效。 */
  public String beginInvalidation() {
    String token = UUID.randomUUID().toString();
    return executeRequired(
        () ->
            redisTemplate.execute(
                BEGIN_INVALIDATION_SCRIPT, List.of(VERSION_KEY, INVALIDATING_KEY), token));
  }

  /** 数据库提交后原子递增权限版本，并仅清理当前失效 token。 */
  public long completeInvalidation(String token) {
    return validVersion(
        executeRequired(
            () ->
                redisTemplate.execute(
                    COMPLETE_INVALIDATION_SCRIPT, List.of(VERSION_KEY, INVALIDATING_KEY), token)));
  }

  /** 数据库回滚后仅清理当前事务持有的失效 token。 */
  public void cancelInvalidation(String token) {
    executeRequired(
        () -> redisTemplate.execute(CANCEL_INVALIDATION_SCRIPT, List.of(INVALIDATING_KEY), token));
  }

  /** 恢复提交后遗留的失效标记，并确保旧权限缓存版本失效。 */
  public long retryPendingInvalidation() {
    return validVersion(
        executeRequired(
            () ->
                redisTemplate.execute(
                    RETRY_INVALIDATION_SCRIPT, List.of(VERSION_KEY, INVALIDATING_KEY))));
  }

  /** 尝试获取员工权限重建锁，未获取时返回空。 */
  public String tryAcquireRebuildLock(long employeeId) {
    String token = UUID.randomUUID().toString();
    Boolean acquired =
        executeRequired(
            () ->
                redisTemplate
                    .opsForValue()
                    .setIfAbsent(REBUILD_LOCK_PREFIX + employeeId, token, REBUILD_LOCK_TTL));
    return Boolean.TRUE.equals(acquired) ? token : null;
  }

  /** 仅由持有相同 token 的调用方释放员工权限重建锁。 */
  public void releaseRebuildLock(long employeeId, String token) {
    executeRequired(
        () ->
            redisTemplate.execute(
                RELEASE_REBUILD_LOCK_SCRIPT, List.of(REBUILD_LOCK_PREFIX + employeeId), token));
  }

  private EmployeePermissionCache readCache(String value) {
    if (value == null) {
      throw unavailable();
    }
    try {
      JsonNode root = objectMapper.readTree(value);
      JsonNode version = root == null ? null : root.get("version");
      JsonNode apiPaths = root == null ? null : root.get("apiPaths");
      if (root == null
          || !root.isObject()
          || version == null
          || !version.isIntegralNumber()
          || !version.canConvertToLong()
          || version.longValue() < 0
          || apiPaths == null
          || !apiPaths.isArray()) {
        throw unavailable();
      }
      EmployeePermissionCache cache = objectMapper.treeToValue(root, EmployeePermissionCache.class);
      validateCache(cache);
      return cache;
    } catch (JsonProcessingException | RuntimeException exception) {
      if (exception instanceof AuthenticationInfrastructureException infrastructureException) {
        throw infrastructureException;
      }
      throw new AuthenticationInfrastructureException("认证服务暂不可用", exception);
    }
  }

  private String writeCache(EmployeePermissionCache cache) {
    try {
      return objectMapper.writeValueAsString(cache);
    } catch (JsonProcessingException | RuntimeException exception) {
      throw new AuthenticationInfrastructureException("认证服务暂不可用", exception);
    }
  }

  private void validateCache(EmployeePermissionCache cache) {
    if (cache == null || cache.getVersion() < 0 || cache.getApiPaths() == null) {
      throw unavailable();
    }
    Set<String> apiPaths = cache.getApiPaths();
    if (apiPaths.stream().anyMatch(path -> path == null || path.isBlank())) {
      throw unavailable();
    }
  }

  private long validVersion(Long version) {
    if (version < 0) {
      throw unavailable();
    }
    return version;
  }

  private static DefaultRedisScript<Long> script(String source) {
    return new DefaultRedisScript<>(source, Long.class);
  }

  private AuthenticationInfrastructureException unavailable() {
    return new AuthenticationInfrastructureException("认证服务暂不可用");
  }

  private <T> T executeRequired(Supplier<T> operation) {
    T result;
    try {
      result = operation.get();
    } catch (DataAccessException exception) {
      throw new AuthenticationInfrastructureException("认证服务暂不可用", exception);
    }
    if (result == null) {
      throw unavailable();
    }
    return result;
  }
}
