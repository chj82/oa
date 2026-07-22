package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.model.system.cache.LoginSessionCache;
import com.oa.service.system.store.StringRedisSessionStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionServiceTest {
  private StringRedisSessionStore store;
  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    store = mock(StringRedisSessionStore.class);
    sessionService = new SessionService(store);
  }

  @Test
  void 创建至少256位随机令牌且存储中不出现原令牌() {
    String first = sessionService.createSession(session(7L));
    String second = sessionService.createSession(session(7L));
    assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
    assertNotEquals(first, second);
    String firstHash = sha256(first);
    String secondHash = sha256(second);
    verify(store)
        .createSessionAtomically(
            eq("admin:session:" + firstHash),
            any(LoginSessionCache.class),
            eq("admin:employee-sessions:7"),
            eq(firstHash),
            eq(Duration.ofDays(1)));
    verify(store)
        .createSessionAtomically(
            eq("admin:session:" + secondHash),
            any(LoginSessionCache.class),
            eq("admin:employee-sessions:7"),
            eq(secondHash),
            eq(Duration.ofDays(1)));
  }

  @Test
  void 恢复有效会话并续期一天() {
    String token = sessionService.createSession(session(7L));
    when(store.getAndRefreshAtomically(anyString(), anyString(), any())).thenReturn(session(7L));
    assertEquals(7L, sessionService.getAndRefresh(token).getEmployeeId());
    verify(store)
        .getAndRefreshAtomically(
            anyString(), eq("admin:employee-sessions:"), eq(Duration.ofDays(1)));
  }

  @Test
  void 恢复有效会话时惰性清理员工索引中的过期成员() {
    String token = "valid-token";
    String currentHash = sha256(token);
    String expiredHash = "expired-hash";
    LoginSessionCache session = session(7L);
    when(store.getAndRefreshAtomically(
            "admin:session:" + currentHash, "admin:employee-sessions:", Duration.ofDays(1)))
        .thenReturn(session);
    when(store.indexMembers("admin:employee-sessions:7"))
        .thenReturn(Set.of(currentHash, expiredHash));
    when(store.hasSession("admin:session:" + currentHash)).thenReturn(true);
    when(store.hasSession("admin:session:" + expiredHash)).thenReturn(false);

    assertEquals(7L, sessionService.getAndRefresh(token).getEmployeeId());

    verify(store).removeFromIndex("admin:employee-sessions:7", expiredHash);
    verify(store, never()).removeFromIndex("admin:employee-sessions:7", currentHash);
  }

  @Test
  void 无效令牌不续期() {
    when(store.getAndRefreshAtomically(anyString(), anyString(), any())).thenReturn(null);
    assertNull(sessionService.getAndRefresh("invalid"));
    verify(store, never()).indexMembers(anyString());
  }

  @Test
  void 退出删除会话和员工索引成员() {
    LoginSessionCache session = session(7L);
    String token = sessionService.createSession(session);
    when(store.get(anyString())).thenReturn(session);
    sessionService.removeSession(token);
    verify(store)
        .removeSessionAtomically(
            anyString(), eq("admin:employee-sessions:7"), anyString(), eq(Duration.ofDays(1)));
  }

  @Test
  void 使员工全部会话失效并清理过期索引成员() {
    sessionService.invalidateEmployeeSessions(7L);
    verify(store)
        .invalidateEmployeeSessionsAtomically("admin:employee-sessions:7", "admin:session:");
  }

  private LoginSessionCache session(long employeeId) {
    LoginSessionCache session = new LoginSessionCache();
    session.setEmployeeId(employeeId);
    session.setUsername("user");
    session.setName("测试员工");
    session.setSuperuser(false);
    return session;
  }

  private String sha256(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
