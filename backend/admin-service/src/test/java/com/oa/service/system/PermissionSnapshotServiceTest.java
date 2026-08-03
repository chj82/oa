package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.ResourcePermissionSnapshot;
import com.oa.dao.system.SystemVersionMapper;
import com.oa.service.system.store.StringRedisAuthorizationStore;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 资源权限快照版本协调测试。 */
class PermissionSnapshotServiceTest {
  private PermissionSnapshotService service;

  @AfterEach
  void tearDown() {
    if (service != null) {
      service.shutdown();
    }
  }

  /** 启动阶段必须同步加载并发布已验版快照。 */
  @Test
  void 启动同步初始化快照() {
    PermissionSnapshotLoader loader = mock(PermissionSnapshotLoader.class);
    StringRedisAuthorizationStore store = mock(StringRedisAuthorizationStore.class);
    SystemVersionMapper versionMapper = mock(SystemVersionMapper.class);
    ResourcePermissionSnapshot snapshot = snapshot(8L);
    when(loader.load()).thenReturn(snapshot);
    when(store.advanceSnapshotVersion(8L)).thenReturn(8L);
    when(versionMapper.selectVersion("permission_snapshot")).thenReturn(8L);
    service = new PermissionSnapshotService(loader, store, versionMapper);

    service.initializeSnapshot();

    assertSame(snapshot, service.currentSnapshot());
  }

  /** 定时发现版本变化时请求必须继续返回旧快照并由单线程后台刷新。 */
  @Test
  void 版本变化时非阻塞使用旧快照() throws Exception {
    PermissionSnapshotLoader loader = mock(PermissionSnapshotLoader.class);
    StringRedisAuthorizationStore store = mock(StringRedisAuthorizationStore.class);
    SystemVersionMapper versionMapper = mock(SystemVersionMapper.class);
    ResourcePermissionSnapshot oldSnapshot = snapshot(1L);
    ResourcePermissionSnapshot newSnapshot = snapshot(2L);
    CountDownLatch refreshStarted = new CountDownLatch(1);
    CountDownLatch allowRefresh = new CountDownLatch(1);
    when(loader.load())
        .thenReturn(oldSnapshot)
        .thenAnswer(
            ignored -> {
              refreshStarted.countDown();
              allowRefresh.await(2, TimeUnit.SECONDS);
              return newSnapshot;
            });
    when(store.advanceSnapshotVersion(1L)).thenReturn(1L);
    when(versionMapper.selectVersion("permission_snapshot")).thenReturn(1L, 2L, 2L, 2L);
    service = new PermissionSnapshotService(loader, store, versionMapper);
    service.initializeSnapshot();

    long start = System.nanoTime();
    service.refreshIfPersistentVersionChanged();
    assertSame(oldSnapshot, service.currentSnapshot());
    assertEquals(true, refreshStarted.await(1, TimeUnit.SECONDS));
    assertEquals(true, System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1));
    assertSame(oldSnapshot, service.currentSnapshot());

    allowRefresh.countDown();
    for (int index = 0; index < 50 && service.currentSnapshot() != newSnapshot; index++) {
      Thread.sleep(10L);
    }
    assertSame(newSnapshot, service.currentSnapshot());
  }

  /** 后台刷新失败必须保留旧快照并允许后续重试。 */
  @Test
  void 后台刷新失败保留旧快照() throws Exception {
    PermissionSnapshotLoader loader = mock(PermissionSnapshotLoader.class);
    StringRedisAuthorizationStore store = mock(StringRedisAuthorizationStore.class);
    SystemVersionMapper versionMapper = mock(SystemVersionMapper.class);
    ResourcePermissionSnapshot oldSnapshot = snapshot(1L);
    when(loader.load()).thenReturn(oldSnapshot).thenThrow(new IllegalStateException("数据库异常"));
    when(store.advanceSnapshotVersion(1L)).thenReturn(1L);
    when(versionMapper.selectVersion("permission_snapshot")).thenReturn(1L, 2L, 2L);
    service = new PermissionSnapshotService(loader, store, versionMapper);
    service.initializeSnapshot();

    service.refreshIfPersistentVersionChanged();
    Thread.sleep(100L);

    assertSame(oldSnapshot, service.currentSnapshot());
  }

  /** 数据库版本变化时定时任务必须推进Redis并主动刷新本地快照。 */
  @Test
  void 定时检查数据库版本主动刷新快照() throws Exception {
    PermissionSnapshotLoader loader = mock(PermissionSnapshotLoader.class);
    StringRedisAuthorizationStore store = mock(StringRedisAuthorizationStore.class);
    SystemVersionMapper versionMapper = mock(SystemVersionMapper.class);
    ResourcePermissionSnapshot oldSnapshot = snapshot(1L);
    ResourcePermissionSnapshot newSnapshot = snapshot(2L);
    CountDownLatch refreshed = new CountDownLatch(1);
    when(loader.load())
        .thenReturn(oldSnapshot)
        .thenAnswer(
            ignored -> {
              refreshed.countDown();
              return newSnapshot;
            });
    when(store.advanceSnapshotVersion(1L)).thenReturn(1L);
    when(versionMapper.selectVersion("permission_snapshot")).thenReturn(1L, 2L, 2L, 2L);
    when(store.advanceSnapshotVersion(2L)).thenReturn(2L);
    service = new PermissionSnapshotService(loader, store, versionMapper);
    service.initializeSnapshot();

    service.refreshIfPersistentVersionChanged();

    assertEquals(true, refreshed.await(1, TimeUnit.SECONDS));
    for (int index = 0; index < 50 && service.currentSnapshot() != newSnapshot; index++) {
      Thread.sleep(10L);
    }
    assertSame(newSnapshot, service.currentSnapshot());
    verify(store).advanceSnapshotVersion(2L);
  }

  /** 本地快照为空时不得在请求线程临时加载。 */
  @Test
  void 未初始化时失败关闭() {
    PermissionSnapshotLoader loader = mock(PermissionSnapshotLoader.class);
    StringRedisAuthorizationStore store = mock(StringRedisAuthorizationStore.class);
    SystemVersionMapper versionMapper = mock(SystemVersionMapper.class);
    service = new PermissionSnapshotService(loader, store, versionMapper);

    assertThrows(AuthenticationInfrastructureException.class, service::currentSnapshot);
    verify(loader, never()).load();
  }

  private ResourcePermissionSnapshot snapshot(long version) {
    ResourcePermissionSnapshot snapshot = new ResourcePermissionSnapshot();
    snapshot.setVersion(version);
    snapshot.setNodes(Map.of());
    snapshot.setAllEnabledApiPaths(Set.of());
    return snapshot;
  }
}
