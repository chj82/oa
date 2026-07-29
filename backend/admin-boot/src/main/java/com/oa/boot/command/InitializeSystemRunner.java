package com.oa.boot.command;

import com.oa.service.system.SystemInitializationService;
import com.oa.service.system.SystemInitializationService.InitializationConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 仅在显式命令下执行系统基础数据初始化。 */
@Component
public class InitializeSystemRunner implements ApplicationRunner {
  private final SystemInitializationService initializationService;
  private final String command;
  private final String administratorUsername;
  private final String administratorPassword;

  public InitializeSystemRunner(
      SystemInitializationService initializationService,
      @Value("${app.command:}") String command,
      @Value("${app.initial-admin-username:admin}") String administratorUsername,
      @Value("${app.initial-admin-password:}") String administratorPassword) {
    this.initializationService = initializationService;
    this.command = command;
    this.administratorUsername = administratorUsername;
    this.administratorPassword = administratorPassword;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    if (!"initialize-system".equals(command)) {
      return;
    }
    try {
      initializationService.initialize(administratorUsername, administratorPassword);
    } catch (InitializationConflictException exception) {
      initializationService.initialize(administratorUsername, administratorPassword);
    }
  }
}
