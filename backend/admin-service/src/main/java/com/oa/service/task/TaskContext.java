package com.oa.service.task;

/** 保存当前手动任务线程的可选输入数据。 */
public final class TaskContext {
  private static final ThreadLocal<String> DATA = new ThreadLocal<>();

  private TaskContext() {}

  public static String getData() {
    return DATA.get();
  }

  static void setData(String data) {
    DATA.set(data);
  }

  static void remove() {
    DATA.remove();
  }
}
