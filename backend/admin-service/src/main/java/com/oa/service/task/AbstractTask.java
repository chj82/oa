package com.oa.service.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 提供前置检查、防重入和统一异常处理的任务执行模板。 */
public abstract class AbstractTask implements Task {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTask.class);
  private volatile boolean running;

  @Override
  public void run() {
    if (!preCheck()) {
      LOGGER.info("任务前置检查未通过，不执行，任务={}", getClass().getSimpleName());
      return;
    }
    synchronized (this) {
      if (running) {
        LOGGER.warn("任务正在执行，忽略重复触发，任务={}", getClass().getSimpleName());
        return;
      }
      running = true;
    }
    long startTime = System.currentTimeMillis();
    try {
      execute();
      LOGGER.info(
          "任务执行完成，任务={}，耗时={}ms",
          getClass().getSimpleName(),
          System.currentTimeMillis() - startTime);
    } catch (Exception exception) {
      LOGGER.error("任务执行失败，任务={}", getClass().getSimpleName(), exception);
    } finally {
      running = false;
    }
  }

  /** 任务执行前检查，返回 false 时跳过本次执行。 */
  public boolean preCheck() {
    return true;
  }

  /** 执行具体任务逻辑。 */
  protected abstract void execute();
}
