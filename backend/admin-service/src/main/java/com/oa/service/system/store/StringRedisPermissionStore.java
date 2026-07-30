package com.oa.service.system.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeePermissionCache;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** 使用 Redis 全局版本控制员工接口权限缓存。 */
@Repository
public class StringRedisPermissionStore {
  private static final String PERMISSION_PREFIX = "admin:permission:";
  private static final String VERSION_KEY = "admin:permission:version";
  private static final String PENDING_SET_KEY = "admin:permission:invalidating:pending";
  private static final String INVALIDATING_LEASE_PREFIX = "admin:permission:invalidating:lease:";
  private static final String REBUILD_LOCK_PREFIX = "admin:permission:rebuild-lock:";
  private static final String CACHE_MISS = "__PERMISSION_CACHE_MISS__";
  private static final String CACHE_INVALIDATING = "__PERMISSION_INVALIDATING__";
  private static final String CACHE_INVALID = "__PERMISSION_CACHE_INVALID__";
  private static final Duration CACHE_TTL = Duration.ofDays(1);
  private static final Duration REBUILD_LOCK_TTL = Duration.ofSeconds(10);
  private static final Duration INVALIDATING_LEASE_TTL = Duration.ofSeconds(60);
  private static final DefaultRedisScript<String> GET_IF_CURRENT_SCRIPT =
      new DefaultRedisScript<>(
          pendingRecoveryScript(1, 3, 4)
              + "if active then return '"
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
              + "or type(cache.resources) ~= 'table' "
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
  private static final DefaultRedisScript<Long> PUT_IF_CURRENT_SCRIPT =
      script(
          pendingRecoveryScript(1, 3, 4)
              + "if active then return 0; end; "
              + "local version = redis.call('GET', KEYS[1]); "
              + "if not version then redis.call('SET', KEYS[1], '0'); version = '0'; end; "
              + "local cacheVersion = ARGV[1]; "
              + "if tostring(version) ~= cacheVersion then return 0; end; "
              + "redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3]); return 1");
  private static final DefaultRedisScript<Long> CURRENT_VERSION_SCRIPT =
      new DefaultRedisScript<>(
          "local version = redis.call('GET', KEYS[1]); "
              + "if not version then redis.call('SET', KEYS[1], '0'); return 0; end; "
              + "return tonumber(version)",
          Long.class);
  private static final DefaultRedisScript<String> BEGIN_INVALIDATION_SCRIPT =
      new DefaultRedisScript<>(
          pendingRecoveryScript(1, 2, 3)
              + "redis.call('SADD', KEYS[2], ARGV[1]); "
              + "redis.call('SET', KEYS[3] .. ARGV[1], ARGV[1], 'PX', ARGV[2]); "
              + "return ARGV[1]",
          String.class);
  private static final DefaultRedisScript<Long> COMPLETE_INVALIDATION_SCRIPT =
      script(
          "local version = redis.call('INCR', KEYS[1]); "
              + "redis.call('SREM', KEYS[2], ARGV[1]); "
              + "redis.call('DEL', KEYS[3] .. ARGV[1]); "
              + "return version");
  private static final DefaultRedisScript<Long> CANCEL_INVALIDATION_SCRIPT =
      script(
          "local removed = redis.call('SREM', KEYS[1], ARGV[1]); "
              + "redis.call('DEL', KEYS[2] .. ARGV[1]); return removed");
  private static final DefaultRedisScript<Long> RETRY_INVALIDATION_SCRIPT =
      script(
          pendingRecoveryScript(1, 2, 3)
              + "local version = redis.call('GET', KEYS[1]); "
              + "if not version then redis.call('SET', KEYS[1], '0'); return 0; end; "
              + "return tonumber(version)");
  private static final DefaultRedisScript<Long> RELEASE_REBUILD_LOCK_SCRIPT =
      script(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); "
              + "end; return 0");

  private final StringRedisTemplate redisTemplate;
  private final RedisCacheJsonCodec jsonCodec;

  public StringRedisPermissionStore(
      StringRedisTemplate redisTemplate, RedisCacheJsonCodec jsonCodec) {
    this.redisTemplate = redisTemplate;
    this.jsonCodec = jsonCodec;
  }

  /** 单次 Lua 读取全局版本和员工缓存，仅返回版本匹配的缓存。 */
  public EmployeePermissionCache getIfCurrent(long employeeId) {
    String value =
        executeRequired(
            () ->
                redisTemplate.execute(
                    GET_IF_CURRENT_SCRIPT,
                    List.of(
                        VERSION_KEY,
                        PERMISSION_PREFIX + employeeId,
                        PENDING_SET_KEY,
                        INVALIDATING_LEASE_PREFIX)));
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

  /** 仅在全局版本仍匹配且没有活动失效事务时原子写入员工权限缓存。 */
  public boolean putIfCurrent(long employeeId, EmployeePermissionCache cache) {
    validateCache(cache);
    Long result =
        executeRequired(
            () ->
                redisTemplate.execute(
                    PUT_IF_CURRENT_SCRIPT,
                    List.of(
                        VERSION_KEY,
                        PERMISSION_PREFIX + employeeId,
                        PENDING_SET_KEY,
                        INVALIDATING_LEASE_PREFIX),
                    Long.toString(cache.getVersion()),
                    writeCache(cache),
                    Long.toString(CACHE_TTL.toMillis())));
    return result == 1L;
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
                BEGIN_INVALIDATION_SCRIPT,
                List.of(VERSION_KEY, PENDING_SET_KEY, INVALIDATING_LEASE_PREFIX),
                token,
                Long.toString(INVALIDATING_LEASE_TTL.toMillis())));
  }

  /** 数据库提交后原子递增权限版本，并仅清理当前失效 token。 */
  public long completeInvalidation(String token) {
    return validVersion(
        executeRequired(
            () ->
                redisTemplate.execute(
                    COMPLETE_INVALIDATION_SCRIPT,
                    List.of(VERSION_KEY, PENDING_SET_KEY, INVALIDATING_LEASE_PREFIX),
                    token)));
  }

  /** 数据库回滚后仅清理当前事务持有的失效 token。 */
  public void cancelInvalidation(String token) {
    executeRequired(
        () ->
            redisTemplate.execute(
                CANCEL_INVALIDATION_SCRIPT,
                List.of(PENDING_SET_KEY, INVALIDATING_LEASE_PREFIX),
                token));
  }

  /** 恢复提交后遗留的失效标记，并确保旧权限缓存版本失效。 */
  public long retryPendingInvalidation() {
    return validVersion(
        executeRequired(
            () ->
                redisTemplate.execute(
                    RETRY_INVALIDATION_SCRIPT,
                    List.of(VERSION_KEY, PENDING_SET_KEY, INVALIDATING_LEASE_PREFIX))));
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
      JsonNode root = jsonCodec.readTree(value);
      JsonNode version = root == null ? null : root.get("version");
      JsonNode apiPaths = root == null ? null : root.get("apiPaths");
      JsonNode resources = root == null ? null : root.get("resources");
      if (root == null
          || !root.isObject()
          || version == null
          || !version.isIntegralNumber()
          || !version.canConvertToLong()
          || version.longValue() < 0
          || apiPaths == null
          || !apiPaths.isArray()
          || resources == null
          || !resources.isArray()) {
        throw unavailable();
      }
      EmployeePermissionCache cache = jsonCodec.treeToValue(root, EmployeePermissionCache.class);
      validateCache(cache);
      return cache;
    } catch (RuntimeException exception) {
      if (exception instanceof AuthenticationInfrastructureException infrastructureException) {
        throw infrastructureException;
      }
      throw new AuthenticationInfrastructureException("认证服务暂不可用", exception);
    }
  }

  private String writeCache(EmployeePermissionCache cache) {
    try {
      return jsonCodec.write(cache);
    } catch (RuntimeException exception) {
      throw new AuthenticationInfrastructureException("认证服务暂不可用", exception);
    }
  }

  private void validateCache(EmployeePermissionCache cache) {
    if (cache == null
        || cache.getVersion() < 0
        || cache.getApiPaths() == null
        || cache.getResources() == null) {
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

  private static String pendingRecoveryScript(
      int versionKey, int pendingSetKey, int leasePrefixKey) {
    return "local active = false; local recovered = false; "
        + "local tokens = redis.call('SMEMBERS', KEYS["
        + pendingSetKey
        + "]); "
        + "for _, token in ipairs(tokens) do "
        + "if redis.call('EXISTS', KEYS["
        + leasePrefixKey
        + "] .. token) == 1 then active = true; "
        + "else redis.call('SREM', KEYS["
        + pendingSetKey
        + "], token); recovered = true; end; end; "
        + "if recovered then redis.call('INCR', KEYS["
        + versionKey
        + "]); end; ";
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
