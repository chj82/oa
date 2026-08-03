package com.oa.service.system.store;

import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeeAuthorizationCache;
import com.oa.common.model.system.cache.RoleAuthorizationCache;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** 使用 Redis 保存员工和角色授权关系及资源快照版本镜像。 */
@Repository
public class StringRedisAuthorizationStore {
  private static final String EMPLOYEE_KEY_PREFIX = "admin:employee-auth:";
  private static final String ROLE_KEY_PREFIX = "admin:role-auth:";
  private static final String SNAPSHOT_VERSION_KEY = "admin:permission:snapshot:version";
  private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(10);
  private static final DefaultRedisScript<Long> ADVANCE_SNAPSHOT_VERSION_SCRIPT =
      new DefaultRedisScript<>(
          "local current = tonumber(redis.call('GET', KEYS[1])); "
              + "local candidate = tonumber(ARGV[1]); "
              + "if not current or candidate > current then "
              + "redis.call('SET', KEYS[1], candidate); return candidate; end; return current",
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final RedisCacheJsonCodec jsonCodec;

  public StringRedisAuthorizationStore(
      StringRedisTemplate redisTemplate, RedisCacheJsonCodec jsonCodec) {
    this.redisTemplate = redisTemplate;
    this.jsonCodec = jsonCodec;
  }

  public EmployeeAuthorizationCache getEmployee(long employeeId) {
    String value = execute(() -> redisTemplate.opsForValue().get(employeeKey(employeeId)));
    return value == null ? null : read(value, EmployeeAuthorizationCache.class);
  }

  public void putEmployee(EmployeeAuthorizationCache cache) {
    execute(
        () -> {
          redisTemplate
              .opsForValue()
              .set(employeeKey(cache.getEmployeeId()), write(cache), AUTHORIZATION_TTL);
          return null;
        });
  }

  public void deleteEmployee(long employeeId) {
    execute(() -> redisTemplate.delete(employeeKey(employeeId)));
  }

  public Map<Long, RoleAuthorizationCache> multiGetRoles(Collection<Long> roleIds) {
    if (roleIds.isEmpty()) {
      return Map.of();
    }
    List<Long> orderedRoleIds = new ArrayList<>(roleIds);
    List<String> keys = orderedRoleIds.stream().map(this::roleKey).toList();
    List<String> values = execute(() -> redisTemplate.opsForValue().multiGet(keys));
    if (values == null || values.size() != orderedRoleIds.size()) {
      throw unavailable();
    }
    Map<Long, RoleAuthorizationCache> result = new LinkedHashMap<>();
    for (int index = 0; index < orderedRoleIds.size(); index++) {
      String value = values.get(index);
      if (value != null) {
        result.put(orderedRoleIds.get(index), read(value, RoleAuthorizationCache.class));
      }
    }
    return result;
  }

  public void putRoles(Collection<RoleAuthorizationCache> caches) {
    execute(
        () -> {
          for (RoleAuthorizationCache cache : caches) {
            redisTemplate
                .opsForValue()
                .set(roleKey(cache.getRoleId()), write(cache), AUTHORIZATION_TTL);
          }
          return null;
        });
  }

  public void deleteRole(long roleId) {
    execute(() -> redisTemplate.delete(roleKey(roleId)));
  }

  public Long getSnapshotVersion() {
    String value = execute(() -> redisTemplate.opsForValue().get(SNAPSHOT_VERSION_KEY));
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException exception) {
      throw unavailable(exception);
    }
  }

  public long advanceSnapshotVersion(long candidateVersion) {
    Long version =
        execute(
            () ->
                redisTemplate.execute(
                    ADVANCE_SNAPSHOT_VERSION_SCRIPT,
                    List.of(SNAPSHOT_VERSION_KEY),
                    Long.toString(candidateVersion)));
    if (version == null) {
      throw unavailable();
    }
    return version;
  }

  private String employeeKey(long employeeId) {
    return EMPLOYEE_KEY_PREFIX + employeeId;
  }

  private String roleKey(long roleId) {
    return ROLE_KEY_PREFIX + roleId;
  }

  private String write(Object value) {
    try {
      return jsonCodec.write(value);
    } catch (RedisCacheJsonException exception) {
      throw unavailable(exception);
    }
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return jsonCodec.read(value, type);
    } catch (RedisCacheJsonException exception) {
      throw unavailable(exception);
    }
  }

  private <T> T execute(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException exception) {
      throw unavailable(exception);
    }
  }

  private AuthenticationInfrastructureException unavailable() {
    return new AuthenticationInfrastructureException("认证服务暂不可用");
  }

  private AuthenticationInfrastructureException unavailable(Throwable cause) {
    return new AuthenticationInfrastructureException("认证服务暂不可用", cause);
  }
}
