package com.oa.common.context;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.oa.common.model.system.vo.CurrentEmployeeVO;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 当前登录员工线程上下文测试。 */
class CurrentEmployeeContextTest {
  @AfterEach
  void tearDown() {
    CurrentEmployeeContext.clear();
  }

  /** 当前线程可以设置、读取并清理员工。 */
  @Test
  void 当前线程可以设置读取并清理员工() {
    CurrentEmployeeVO employee = employee(7L);

    CurrentEmployeeContext.set(employee);

    assertSame(employee, CurrentEmployeeContext.get());
    CurrentEmployeeContext.clear();
    assertNull(CurrentEmployeeContext.get());
  }

  /** 子线程不能继承父线程的当前员工。 */
  @Test
  void 子线程不能继承当前员工() throws Exception {
    CurrentEmployeeContext.set(employee(7L));
    AtomicReference<CurrentEmployeeVO> childEmployee = new AtomicReference<>();
    Thread thread = new Thread(() -> childEmployee.set(CurrentEmployeeContext.get()));

    thread.start();
    thread.join();

    assertNull(childEmployee.get());
  }

  private CurrentEmployeeVO employee(long id) {
    CurrentEmployeeVO employee = new CurrentEmployeeVO();
    employee.setId(id);
    return employee;
  }
}
