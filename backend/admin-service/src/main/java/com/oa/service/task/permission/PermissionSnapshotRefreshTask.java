package com.oa.service.task.permission;

import com.oa.service.system.PermissionSnapshotService;
import com.oa.service.task.AbstractTask;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 检查持久版本并刷新本地权限快照的定时任务。 */
@Component
public class PermissionSnapshotRefreshTask extends AbstractTask {
  private final PermissionSnapshotService permissionSnapshotService;

  public PermissionSnapshotRefreshTask(PermissionSnapshotService permissionSnapshotService) {
    this.permissionSnapshotService = permissionSnapshotService;
  }

  /** 每五秒检查一次权限快照版本。 */
  @Scheduled(fixedDelay = 5_000L, initialDelay = 5_000L)
  public void schedule() {
    run();
  }

  @Override
  protected void execute() {
    permissionSnapshotService.refreshIfPersistentVersionChanged();
  }
}
