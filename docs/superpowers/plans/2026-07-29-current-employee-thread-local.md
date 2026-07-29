# 当前登录员工 ThreadLocal 改造实施计划

> **执行要求：** 按任务顺序逐项执行，每个行为先确认测试因目标能力缺失而失败，再编写最小实现。用户未要求 Git 提交，本计划不执行 commit。

**目标：** 将当前登录员工从 `HttpServletRequest` 属性迁移到普通 `ThreadLocal` 请求上下文，并确保请求线程复用时不会残留身份。

**架构：** `admin-common` 提供静态 `CurrentEmployeeContext`；`TokenAuthenticationFilter` 负责认证成功后的设置及 `finally` 清理；权限拦截器和 Controller 只读取上下文。Service 方法签名及业务规则保持不变。

**技术栈：** Java 17、Spring Boot 3、Servlet Filter、Spring MVC、JUnit 5、Mockito、Maven。

---

## 文件结构

- 新增 `backend/admin-common/src/main/java/com/oa/common/context/CurrentEmployeeContext.java`：保存当前请求线程的登录员工。
- 新增 `backend/admin-common/src/test/java/com/oa/common/context/CurrentEmployeeContextTest.java`：验证读写、清理和线程隔离。
- 修改 `backend/admin-boot/src/main/java/com/oa/boot/security/TokenAuthenticationFilter.java`：设置并清理上下文。
- 修改 `backend/admin-boot/src/test/java/com/oa/boot/security/TokenAuthenticationFilterTest.java`：验证过滤器链内可见及正常、异常清理。
- 修改 `backend/admin-boot/src/main/java/com/oa/boot/security/ResourceApiAuthorizationInterceptor.java`：从上下文读取当前员工。
- 修改 `backend/admin-boot/src/test/java/com/oa/boot/security/ResourceApiAuthorizationInterceptorTest.java`：改用上下文布置认证身份并清理。
- 修改 `backend/admin-action/src/main/java/com/oa/action/controller/system/AuthController.java`：当前员工接口读取上下文。
- 修改 `backend/admin-action/src/main/java/com/oa/action/controller/system/EmployeeController.java`：员工写操作读取上下文，Service 参数不变。
- 修改 `backend/admin-common/src/main/java/com/oa/common/constant/AuthenticationConstants.java`：删除当前员工请求属性常量。

### 任务 1：建立当前员工线程上下文

- [ ] 新增 `CurrentEmployeeContextTest`，覆盖同线程设置、读取、清理以及新线程无法读取父线程员工：

```java
@Test
void 当前线程可以设置读取并清理员工() {
  CurrentEmployeeVO employee = employee(7L);
  CurrentEmployeeContext.set(employee);
  assertSame(employee, CurrentEmployeeContext.get());
  CurrentEmployeeContext.clear();
  assertNull(CurrentEmployeeContext.get());
}

@Test
void 子线程不能继承当前员工() throws Exception {
  CurrentEmployeeContext.set(employee(7L));
  AtomicReference<CurrentEmployeeVO> childEmployee = new AtomicReference<>();
  Thread thread = new Thread(() -> childEmployee.set(CurrentEmployeeContext.get()));
  thread.start();
  thread.join();
  assertNull(childEmployee.get());
}
```

- [ ] 运行 `cd backend && mvn -pl admin-common -Dtest=CurrentEmployeeContextTest test`，预期因 `CurrentEmployeeContext` 不存在而编译失败。
- [ ] 新增最小上下文实现，使用 `private static final ThreadLocal<CurrentEmployeeVO>`，公开 `set(CurrentEmployeeVO)`、`get()`、`clear()`，`clear()` 必须调用 `ThreadLocal.remove()`。
- [ ] 运行同一测试，预期通过。

### 任务 2：由认证过滤器管理上下文生命周期

- [ ] 修改 `TokenAuthenticationFilterTest`：过滤器链回调内通过 `CurrentEmployeeContext.get()` 断言员工和资源；过滤器正常结束后断言上下文为空；下游抛出异常后同样断言上下文为空。每个测试后调用 `CurrentEmployeeContext.clear()`，防止失败测试污染测试线程。
- [ ] 运行 `cd backend && mvn -pl admin-boot -am -Dtest=TokenAuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期“有效 Cookie”测试失败，因为过滤器尚未写入线程上下文。
- [ ] 修改 `TokenAuthenticationFilter`：认证成功并组装 `CurrentEmployeeVO` 后调用 `CurrentEmployeeContext.set(currentEmployee)`；在现有过滤器链 `finally` 中调用 `CurrentEmployeeContext.clear()`，不再设置或删除请求属性。
- [ ] 运行同一测试，预期通过。

### 任务 3：权限拦截器改为读取线程上下文

- [ ] 修改 `ResourceApiAuthorizationInterceptorTest`：认证场景通过 `CurrentEmployeeContext.set(employee)` 布置当前员工，不再设置当前员工请求属性；增加 `@AfterEach` 清理上下文。
- [ ] 运行 `cd backend && mvn -pl admin-boot -am -Dtest=ResourceApiAuthorizationInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期已登录授权场景返回 `401`，因为拦截器仍读取请求属性。
- [ ] 修改 `ResourceApiAuthorizationInterceptor`：通过 `CurrentEmployeeContext.get()` 获取员工；为空时保持现有 `401` 响应，权限校验及 `403`、`503` 行为不变。
- [ ] 运行同一测试，预期通过。

### 任务 4：Controller 改为读取线程上下文

- [ ] 先运行 `cd backend && mvn -pl admin-boot -am -Dtest=AuthControllerTest,EmployeeControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`。在过滤器已迁移、Controller 尚未迁移的状态下，预期当前员工响应或员工自操作场景失败，证明 Controller 仍依赖请求属性。
- [ ] 修改 `AuthController.current()`，删除 `HttpServletRequest` 参数并直接返回 `CurrentEmployeeContext.get()`；退出接口仍保留 `HttpServletRequest` 用于读取 Cookie。
- [ ] 修改 `EmployeeController.delete()` 和 `roles()`，删除只为读取当前员工而存在的 `HttpServletRequest` 参数，通过 `CurrentEmployeeContext.get()` 将员工或员工 ID 显式传给 `EmployeeService`；删除 Controller 内的请求属性读取辅助方法。
- [ ] 运行同一测试，预期通过，并确认 Service 方法签名未改变。

### 任务 5：删除旧请求属性契约并完成验证

- [ ] 删除 `AuthenticationConstants.CURRENT_EMPLOYEE_ATTRIBUTE`，保留 `ADMIN_TOKEN` Cookie 常量。
- [ ] 运行 `rg -n "CURRENT_EMPLOYEE_ATTRIBUTE|setAttribute\(.*CURRENT_EMPLOYEE|getAttribute\(.*CURRENT_EMPLOYEE" backend --glob '*.java'`，预期无输出。
- [ ] 对本次修改的全部 Java 文件执行：

```bash
java -jar tools/google-java-format-1.28.0-all-deps.jar --replace \
  backend/admin-common/src/main/java/com/oa/common/context/CurrentEmployeeContext.java \
  backend/admin-common/src/test/java/com/oa/common/context/CurrentEmployeeContextTest.java \
  backend/admin-common/src/main/java/com/oa/common/constant/AuthenticationConstants.java \
  backend/admin-boot/src/main/java/com/oa/boot/security/TokenAuthenticationFilter.java \
  backend/admin-boot/src/test/java/com/oa/boot/security/TokenAuthenticationFilterTest.java \
  backend/admin-boot/src/main/java/com/oa/boot/security/ResourceApiAuthorizationInterceptor.java \
  backend/admin-boot/src/test/java/com/oa/boot/security/ResourceApiAuthorizationInterceptorTest.java \
  backend/admin-action/src/main/java/com/oa/action/controller/system/AuthController.java \
  backend/admin-action/src/main/java/com/oa/action/controller/system/EmployeeController.java
```

- [ ] 运行 `cd backend && mvn test`，预期全部测试通过。
- [ ] 运行 `cd backend && mvn package`，预期构建成功。
- [ ] 运行 `git diff --check` 并检查差异只包含本次 ThreadLocal 改造及此前用户已有改动；不执行 Git commit、push 或 merge。
