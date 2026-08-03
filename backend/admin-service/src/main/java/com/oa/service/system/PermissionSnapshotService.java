package com.oa.service.system;

import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.ResourcePermissionSnapshot;
import com.oa.dao.system.SystemVersionMapper;
import com.oa.service.system.store.StringRedisAuthorizationStore;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 协调 JVM 资源权限快照的启动加载、版本校准和后台刷新。 */
@Service
public class PermissionSnapshotService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PermissionSnapshotService.class);
  private static final int INITIALIZE_ATTEMPTS = 3;
  private static final String PERMISSION_SNAPSHOT_VERSION_CODE = "permission_snapshot";

  private final PermissionSnapshotLoader loader;
  private final StringRedisAuthorizationStore authorizationStore;
  private final SystemVersionMapper versionMapper;
  private final AtomicReference<ResourcePermissionSnapshot> current = new AtomicReference<>();
  private final AtomicBoolean refreshInProgress = new AtomicBoolean();
  private final ExecutorService refreshExecutor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "permission-snapshot-refresh");
            thread.setDaemon(true);
            return thread;
          });

  public PermissionSnapshotService(
      PermissionSnapshotLoader loader,
      StringRedisAuthorizationStore authorizationStore,
      SystemVersionMapper versionMapper) {
    this.loader = loader;
    this.authorizationStore = authorizationStore;
    this.versionMapper = versionMapper;
  }

  /** 返回当前本地快照。 */
  public ResourcePermissionSnapshot currentSnapshot() {
    ResourcePermissionSnapshot snapshot = current.get();
    if (snapshot == null) {
      throw unavailable();
    }
    return snapshot;
  }

  /** 检查数据库持久版本，发现变化时推进Redis并提交后台刷新。 */
  public void refreshIfPersistentVersionChanged() {
    ResourcePermissionSnapshot snapshot = current.get();
    if (snapshot == null) {
      LOGGER.error("定时检查权限快照版本时本地快照为空");
      return;
    }
    try {
      long persistentVersion = selectPersistentVersion();
      if (persistentVersion != snapshot.getVersion()) {
        authorizationStore.advanceSnapshotVersion(persistentVersion);
        submitRefresh();
      }
    } catch (RuntimeException exception) {
      LOGGER.error("定时检查权限快照版本失败，继续使用旧快照", exception);
    }
  }

  /** 服务启动阶段同步加载并验版，失败时阻止应用启动。 */
  public void initializeSnapshot() {
    for (int attempt = 0; attempt < INITIALIZE_ATTEMPTS; attempt++) {
      ResourcePermissionSnapshot snapshot = loader.load();
      authorizationStore.advanceSnapshotVersion(snapshot.getVersion());
      if (selectPersistentVersion() == snapshot.getVersion()) {
        current.set(snapshot);
        return;
      }
    }
    throw new AuthenticationInfrastructureException("权限快照初始化期间版本持续变化");
  }

  /** 在事务内递增持久版本，提交后再推进 Redis 镜像。 */
  public long incrementPersistentVersion() {
    long version = versionMapper.incrementAndSelectVersion(PERMISSION_SNAPSHOT_VERSION_CODE);
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              advanceMirrorAfterCommit(version);
            }
          });
    } else {
      authorizationStore.advanceSnapshotVersion(version);
    }
    return version;
  }

  private void submitRefresh() {
    if (!refreshInProgress.compareAndSet(false, true)) {
      return;
    }
    refreshExecutor.execute(
        () -> {
          try {
            ResourcePermissionSnapshot candidate = loader.load();
            if (selectPersistentVersion() == candidate.getVersion()) {
              current.set(candidate);
            }
          } catch (RuntimeException exception) {
            LOGGER.error("后台刷新权限快照失败，继续使用旧快照", exception);
          } finally {
            refreshInProgress.set(false);
          }
        });
  }

  private void advanceMirrorAfterCommit(long version) {
    try {
      authorizationStore.advanceSnapshotVersion(version);
      ResourcePermissionSnapshot snapshot = current.get();
      if (snapshot != null && snapshot.getVersion() != version) {
        submitRefresh();
      }
    } catch (RuntimeException exception) {
      LOGGER.error("数据库权限快照版本已提交，但Redis版本镜像推进失败，版本={}", version, exception);
    }
  }

  private long selectPersistentVersion() {
    return versionMapper.selectVersion(PERMISSION_SNAPSHOT_VERSION_CODE);
  }

  private AuthenticationInfrastructureException unavailable() {
    return new AuthenticationInfrastructureException("认证服务暂不可用");
  }

  /** 停止后台刷新线程。 */
  @PreDestroy
  public void shutdown() {
    refreshExecutor.shutdownNow();
  }
}
