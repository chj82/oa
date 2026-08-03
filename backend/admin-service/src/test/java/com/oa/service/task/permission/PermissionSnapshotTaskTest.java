package com.oa.service.task.permission;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oa.service.system.PermissionSnapshotService;
import org.junit.jupiter.api.Test;

/** 权限快照定时任务测试。 */
class PermissionSnapshotTaskTest {

  /** 快照刷新任务统一检查数据库版本并刷新本地快照。 */
  @Test
  void 快照刷新任务执行() {
    PermissionSnapshotService service = mock(PermissionSnapshotService.class);

    new PermissionSnapshotRefreshTask(service).run();

    verify(service).refreshIfPersistentVersionChanged();
  }
}
