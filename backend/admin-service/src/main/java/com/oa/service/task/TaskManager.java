package com.oa.service.task;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/** 注册任务并提供列表查询和异步手动触发能力。 */
@Service
public class TaskManager {
  private final Map<String, Task> taskMap;
  private final ExecutorService executor;

  public TaskManager(Map<String, Task> taskMap) {
    this.taskMap = Map.copyOf(taskMap);
    AtomicInteger threadNumber = new AtomicInteger();
    this.executor =
        Executors.newFixedThreadPool(
            4,
            runnable -> {
              Thread thread = new Thread(runnable, "manual-task-" + threadNumber.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
  }

  /** 按 Bean 名称排序返回全部任务。 */
  public List<String> list() {
    return taskMap.keySet().stream().sorted().toList();
  }

  /** 异步手动执行指定任务。 */
  public void run(String name, String data) {
    Task task = taskMap.get(name);
    if (task == null) {
      throw new BusinessException(ExceptionCode.TASK_NOT_FOUND, "任务不存在，名称=" + name);
    }
    executor.execute(
        () -> {
          try {
            TaskContext.setData(data);
            task.run();
          } finally {
            TaskContext.remove();
          }
        });
  }

  /** 停止手动任务执行线程池。 */
  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }
}
