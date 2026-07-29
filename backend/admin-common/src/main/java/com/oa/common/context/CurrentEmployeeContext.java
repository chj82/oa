package com.oa.common.context;

import com.oa.common.model.system.vo.CurrentEmployeeVO;

/** 当前登录员工请求线程上下文。 */
public final class CurrentEmployeeContext {
  private static final ThreadLocal<CurrentEmployeeVO> CURRENT_EMPLOYEE = new ThreadLocal<>();

  private CurrentEmployeeContext() {}

  /** 设置当前线程的登录员工。 */
  public static void set(CurrentEmployeeVO employee) {
    CURRENT_EMPLOYEE.set(employee);
  }

  /** 获取当前线程的登录员工。 */
  public static CurrentEmployeeVO get() {
    return CURRENT_EMPLOYEE.get();
  }

  /** 清理当前线程的登录员工。 */
  public static void clear() {
    CURRENT_EMPLOYEE.remove();
  }
}
