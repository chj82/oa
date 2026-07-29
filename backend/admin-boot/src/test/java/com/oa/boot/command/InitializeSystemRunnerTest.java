package com.oa.boot.command;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oa.service.system.SystemInitializationService;
import com.oa.service.system.SystemInitializationService.InitializationConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class InitializeSystemRunnerTest {
  @Test
  void 普通启动不访问初始化服务() throws Exception {
    SystemInitializationService service =
        org.mockito.Mockito.mock(SystemInitializationService.class);
    InitializeSystemRunner runner = new InitializeSystemRunner(service, "", "", "");

    runner.run(new DefaultApplicationArguments());

    verify(service, never())
        .initialize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void 显式初始化命令调用初始化服务() throws Exception {
    SystemInitializationService service =
        org.mockito.Mockito.mock(SystemInitializationService.class);
    InitializeSystemRunner runner =
        new InitializeSystemRunner(service, "initialize-system", "admin", "strong-password");

    runner.run(new DefaultApplicationArguments());

    verify(service).initialize("admin", "strong-password");
  }

  @Test
  void 管理员唯一键竞争只重试一次() throws Exception {
    SystemInitializationService service =
        org.mockito.Mockito.mock(SystemInitializationService.class);
    doThrow(new InitializationConflictException(new RuntimeException("竞争")))
        .doNothing()
        .when(service)
        .initialize("admin", "strong-password");
    InitializeSystemRunner runner =
        new InitializeSystemRunner(service, "initialize-system", "admin", "strong-password");

    runner.run(new DefaultApplicationArguments());

    verify(service, times(2)).initialize("admin", "strong-password");
  }

  @Test
  void 管理员连续竞争时第二次异常直接抛出() throws Exception {
    SystemInitializationService service =
        org.mockito.Mockito.mock(SystemInitializationService.class);
    doThrow(new InitializationConflictException(new RuntimeException("竞争")))
        .when(service)
        .initialize("admin", "strong-password");
    InitializeSystemRunner runner =
        new InitializeSystemRunner(service, "initialize-system", "admin", "strong-password");

    assertThrows(
        InitializationConflictException.class, () -> runner.run(new DefaultApplicationArguments()));

    verify(service, times(2)).initialize("admin", "strong-password");
  }
}
