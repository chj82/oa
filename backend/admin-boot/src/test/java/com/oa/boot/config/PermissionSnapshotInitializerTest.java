package com.oa.boot.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oa.service.system.PermissionSnapshotService;
import org.junit.jupiter.api.Test;

/** 权限快照启动初始化器测试。 */
class PermissionSnapshotInitializerTest {

  /** 应用进入可用状态前必须同步初始化快照。 */
  @Test
  void 启动时初始化权限快照() throws Exception {
    PermissionSnapshotService service = mock(PermissionSnapshotService.class);

    new PermissionSnapshotInitializer(service).run(null);

    verify(service).initializeSnapshot();
  }
}
