package com.oa.service.system;

import com.oa.common.model.system.cache.LoginSessionCache;
import com.oa.service.system.store.StringRedisSessionStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** 登录会话服务。 */
@Service
public class SessionService {
  private static final Duration SESSION_TTL = Duration.ofDays(1);
  private static final String SESSION_PREFIX = "admin:session:";
  private static final String INDEX_PREFIX = "admin:employee-sessions:";

  private final StringRedisSessionStore store;
  private final SecureRandom secureRandom = new SecureRandom();

  public SessionService(StringRedisSessionStore store) {
    this.store = store;
  }

  /** 创建会话并返回仅供 Cookie 下发的原始令牌。 */
  public String createSession(LoginSessionCache session) {
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    String tokenHash = hash(token);
    store.createSessionAtomically(
        SESSION_PREFIX + tokenHash,
        session,
        INDEX_PREFIX + session.getEmployeeId(),
        tokenHash,
        SESSION_TTL);
    return token;
  }

  /** 恢复有效会话并刷新空闲有效期，无效令牌返回空。 */
  public LoginSessionCache getAndRefresh(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    String key = SESSION_PREFIX + hash(token);
    LoginSessionCache session = store.getAndRefreshAtomically(key, INDEX_PREFIX, SESSION_TTL);
    if (session != null) {
      cleanExpiredIndexMembers(session.getEmployeeId());
    }
    return session;
  }

  /** 删除指定原始令牌对应的会话。 */
  public void removeSession(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    String tokenHash = hash(token);
    String key = SESSION_PREFIX + tokenHash;
    LoginSessionCache session = store.get(key);
    if (session != null) {
      store.removeSessionAtomically(
          key, INDEX_PREFIX + session.getEmployeeId(), tokenHash, SESSION_TTL);
    }
  }

  /** 删除指定员工的全部会话，供员工禁用、删除和重置密码时统一调用。 */
  public void invalidateEmployeeSessions(long employeeId) {
    String indexKey = INDEX_PREFIX + employeeId;
    store.invalidateEmployeeSessionsAtomically(indexKey, SESSION_PREFIX);
  }

  private void cleanExpiredIndexMembers(long employeeId) {
    String indexKey = INDEX_PREFIX + employeeId;
    for (String tokenHash : store.indexMembers(indexKey)) {
      if (!store.hasSession(SESSION_PREFIX + tokenHash)) {
        store.removeFromIndex(indexKey, tokenHash);
      }
    }
  }

  private String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("当前运行环境不支持SHA-256", exception);
    }
  }
}
