package com.oa.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 通用任务执行模板测试。 */
class AbstractTaskTest {

  /** preCheck不通过时不得执行任务主体。 */
  @Test
  void 前置检查不通过时跳过执行() {
    AtomicInteger executions = new AtomicInteger();
    AbstractTask task =
        new AbstractTask() {
          @Override
          public boolean preCheck() {
            return false;
          }

          @Override
          protected void execute() {
            executions.incrementAndGet();
          }
        };

    task.run();

    assertEquals(0, executions.get());
  }

  /** 同一个任务实例正在运行时必须忽略重复触发。 */
  @Test
  void 同一任务实例禁止并发执行() throws Exception {
    AtomicInteger executions = new AtomicInteger();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AbstractTask task =
        new AbstractTask() {
          @Override
          protected void execute() {
            executions.incrementAndGet();
            started.countDown();
            try {
              release.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
          }
        };
    Thread first = new Thread(task::run);
    first.start();
    started.await(1, TimeUnit.SECONDS);

    task.run();
    release.countDown();
    first.join();

    assertEquals(1, executions.get());
  }
}
