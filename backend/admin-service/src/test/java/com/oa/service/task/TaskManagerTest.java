package com.oa.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 任务注册和手动触发管理测试。 */
class TaskManagerTest {
  private TaskManager manager;

  @AfterEach
  void tearDown() {
    if (manager != null) {
      manager.shutdown();
    }
  }

  /** 任务名称按Spring Bean名称排序返回。 */
  @Test
  void 返回有序任务名称() {
    manager = new TaskManager(Map.of("taskB", () -> {}, "taskA", () -> {}));

    assertEquals(java.util.List.of("taskA", "taskB"), manager.list());
  }

  /** 手动触发异步执行任务并在线程结束后清理任务数据。 */
  @Test
  void 异步触发任务并传递数据() throws Exception {
    AtomicReference<String> received = new AtomicReference<>();
    CountDownLatch completed = new CountDownLatch(1);
    manager =
        new TaskManager(
            Map.of(
                "sampleTask",
                () -> {
                  received.set(TaskContext.getData());
                  completed.countDown();
                }));

    manager.run("sampleTask", "payload");

    completed.await(1, TimeUnit.SECONDS);
    assertEquals("payload", received.get());
  }

  /** 未注册任务必须返回明确业务异常。 */
  @Test
  void 未注册任务拒绝执行() {
    manager = new TaskManager(Map.of());

    BusinessException exception =
        assertThrows(BusinessException.class, () -> manager.run("missingTask", null));

    assertEquals(ExceptionCode.TASK_NOT_FOUND, exception.getExceptionCode());
  }
}
