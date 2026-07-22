package com.oa.service.system.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oa.common.model.system.vo.LoginSessionVO;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisSessionServiceTest {
  private InMemorySessionStore store;
  private RedisSessionService sessionService;

  @BeforeEach
  void setUp() {
    store = new InMemorySessionStore();
    sessionService = new RedisSessionService(store);
  }

  @Test
  void 创建至少256位随机令牌且存储中不出现原令牌() {
    String first = sessionService.createSession(session(7L));
    String second = sessionService.createSession(session(7L));
    assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
    assertNotEquals(first, second);
    assertFalse(store.toString().contains(first));
    assertTrue(store.sessions.keySet().iterator().next().matches("admin:session:[0-9a-f]{64}"));
    assertEquals(Duration.ofDays(1), store.sessionTtl);
  }

  @Test
  void 恢复有效会话并续期一天() {
    String token = sessionService.createSession(session(7L));
    store.sessionTtl = Duration.ofMinutes(1);
    assertEquals(7L, sessionService.getAndRefresh(token).getEmployeeId());
    assertEquals(Duration.ofDays(1), store.sessionTtl);
  }

  @Test
  void 无效令牌不续期() {
    store.sessionTtl = Duration.ofMinutes(1);
    assertNull(sessionService.getAndRefresh("invalid"));
    assertEquals(Duration.ofMinutes(1), store.sessionTtl);
  }

  @Test
  void 退出删除会话和员工索引成员() {
    String token = sessionService.createSession(session(7L));
    sessionService.removeSession(token);
    assertTrue(store.sessions.isEmpty());
    assertTrue(store.indexes.get("admin:employee-sessions:7").isEmpty());
  }

  @Test
  void 使员工全部会话失效并清理过期索引成员() {
    String first = sessionService.createSession(session(7L));
    String second = sessionService.createSession(session(7L));
    store.indexes.get("admin:employee-sessions:7").add("expired-hash");
    sessionService.invalidateEmployeeSessions(7L);
    assertNull(sessionService.getAndRefresh(first));
    assertNull(sessionService.getAndRefresh(second));
    assertFalse(store.indexes.containsKey("admin:employee-sessions:7"));
  }

  private LoginSessionVO session(long employeeId) {
    LoginSessionVO session = new LoginSessionVO();
    session.setEmployeeId(employeeId);
    session.setUsername("user");
    session.setName("测试员工");
    session.setSuperuser(false);
    return session;
  }

  private static class InMemorySessionStore implements SessionStore {
    private final Map<String, LoginSessionVO> sessions = new HashMap<>();
    private final Map<String, Set<String>> indexes = new HashMap<>();
    private Duration sessionTtl;

    public void save(String key, LoginSessionVO session, Duration ttl) {
      sessions.put(key, session);
      sessionTtl = ttl;
    }

    public LoginSessionVO get(String key) { return sessions.get(key); }

    public void expire(String key, Duration ttl) {
      if (sessions.containsKey(key)) sessionTtl = ttl;
    }

    public void delete(String key) { sessions.remove(key); }

    public void addToIndex(String key, String hash) {
      indexes.computeIfAbsent(key, ignored -> new HashSet<>()).add(hash);
    }

    public Set<String> indexMembers(String key) {
      return new HashSet<>(indexes.getOrDefault(key, Set.of()));
    }

    public void removeFromIndex(String key, String hash) {
      indexes.getOrDefault(key, Set.of()).remove(hash);
    }

    public void deleteIndex(String key) { indexes.remove(key); }

    public String toString() { return sessions.toString() + indexes; }
  }
}
