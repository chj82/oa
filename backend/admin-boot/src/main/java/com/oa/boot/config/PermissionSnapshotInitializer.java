package com.oa.boot.config;

import com.oa.service.system.PermissionSnapshotService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 在应用进入可用状态前同步完成权限快照初始化。 */
@Component
public class PermissionSnapshotInitializer implements ApplicationRunner {
  private final PermissionSnapshotService permissionSnapshotService;

  public PermissionSnapshotInitializer(PermissionSnapshotService permissionSnapshotService) {
    this.permissionSnapshotService = permissionSnapshotService;
  }

  @Override
  public void run(ApplicationArguments args) {
    permissionSnapshotService.initializeSnapshot();
  }
}
