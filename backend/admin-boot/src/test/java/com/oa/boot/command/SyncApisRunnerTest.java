package com.oa.boot.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.SystemApiDefinitionDTO;
import com.oa.service.system.SystemApiService;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 显式接口同步命令测试。 */
class SyncApisRunnerTest {
  @Mock private RequestMappingHandlerMapping handlerMapping;
  @Mock private SystemApiService systemApiService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  /** 普通启动不得扫描接口或修改接口目录。 */
  @Test
  void 非同步命令不执行任何同步行为() throws Exception {
    SyncApisRunner runner = new SyncApisRunner(handlerMapping, systemApiService, "");

    runner.run(new DefaultApplicationArguments());

    verify(handlerMapping, never()).getHandlerMethods();
    verify(systemApiService, never()).sync(org.mockito.ArgumentMatchers.anyList());
  }

  /** 同步命令按 MVC 路由模板和 Operation 元数据同步，并排除固定白名单。 */
  @Test
  void 同步命令收集受资源鉴权的接口() throws Exception {
    Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
    handlers.put(mapping("/api/auth/login", HttpMethod.POST), handler("login"));
    handlers.put(mapping("/api/auth/current", HttpMethod.GET), handler("current"));
    handlers.put(mapping("/api/orders/{id}", HttpMethod.GET), handler("order"));
    when(handlerMapping.getHandlerMethods()).thenReturn(handlers);
    SyncApisRunner runner = new SyncApisRunner(handlerMapping, systemApiService, "sync-apis");

    runner.run(new DefaultApplicationArguments());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SystemApiDefinitionDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(systemApiService).sync(captor.capture());
    assertEquals(1, captor.getValue().size());
    SystemApiDefinitionDTO definition = captor.getValue().get(0);
    assertEquals("查询订单", definition.getName());
    assertEquals("/api/orders/{id}", definition.getPath());
    assertEquals("按订单ID查询订单详情", definition.getDescription());
  }

  /** 受保护接口缺少 Operation 说明时拒绝同步。 */
  @Test
  void 缺少Operation说明时同步失败() throws Exception {
    when(handlerMapping.getHandlerMethods())
        .thenReturn(
            Map.of(mapping("/api/no-operation", HttpMethod.GET), handler("withoutOperation")));
    SyncApisRunner runner = new SyncApisRunner(handlerMapping, systemApiService, "sync-apis");

    BusinessException exception =
        assertThrows(BusinessException.class, () -> runner.run(new DefaultApplicationArguments()));

    assertEquals(ExceptionCode.SYSTEM_API_OPERATION_MISSING, exception.getExceptionCode());
    verify(systemApiService, never()).sync(org.mockito.ArgumentMatchers.anyList());
  }

  /** 单个 Handler 同时声明多个 HTTP Method 时按方法配置无效拒绝同步。 */
  @Test
  void 同一路径声明多个Method时同步失败() throws Exception {
    when(handlerMapping.getHandlerMethods())
        .thenReturn(
            Map.of(
                RequestMappingInfo.paths("/api/orders")
                    .methods(
                        org.springframework.web.bind.annotation.RequestMethod.GET,
                        org.springframework.web.bind.annotation.RequestMethod.POST)
                    .build(),
                handler("order")));
    SyncApisRunner runner = new SyncApisRunner(handlerMapping, systemApiService, "sync-apis");

    BusinessException exception =
        assertThrows(BusinessException.class, () -> runner.run(new DefaultApplicationArguments()));

    assertEquals(ExceptionCode.SYSTEM_API_METHOD_INVALID, exception.getExceptionCode());
    verify(systemApiService, never()).sync(org.mockito.ArgumentMatchers.anyList());
  }

  /** 受保护 Handler 未声明 HTTP Method 时按方法配置无效拒绝同步。 */
  @Test
  void 未声明Method时同步失败() throws Exception {
    when(handlerMapping.getHandlerMethods())
        .thenReturn(Map.of(RequestMappingInfo.paths("/api/orders").build(), handler("order")));
    SyncApisRunner runner = new SyncApisRunner(handlerMapping, systemApiService, "sync-apis");

    BusinessException exception =
        assertThrows(BusinessException.class, () -> runner.run(new DefaultApplicationArguments()));

    assertEquals(ExceptionCode.SYSTEM_API_METHOD_INVALID, exception.getExceptionCode());
    verify(systemApiService, never()).sync(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void Operation摘要为空时同步失败() {
    assertInvalidDefinition("/api/orders", "   ", "描述");
  }

  @Test
  void Operation摘要超过100字符时同步失败() {
    assertInvalidDefinition("/api/orders", "a".repeat(101), "描述");
  }

  @Test
  void 接口路径超过255字符时同步失败() {
    assertInvalidDefinition("/api/" + "a".repeat(251), "查询订单", "描述");
  }

  @Test
  void Operation描述超过500字符时同步失败() {
    assertInvalidDefinition("/api/orders", "查询订单", "a".repeat(501));
  }

  @Test
  void 接口定义边界长度可以同步() {
    String path = "/api/" + "a".repeat(250);
    HandlerMethod handlerMethod = operationHandler("a".repeat(100), "b".repeat(500));
    when(handlerMapping.getHandlerMethods())
        .thenReturn(Map.of(mapping(path, HttpMethod.GET), handlerMethod));
    SyncApisRunner runner = new SyncApisRunner(handlerMapping, systemApiService, "sync-apis");

    runner.run(new DefaultApplicationArguments());

    verify(systemApiService).sync(org.mockito.ArgumentMatchers.anyList());
  }

  private RequestMappingInfo mapping(String path, HttpMethod method) {
    return RequestMappingInfo.paths(path)
        .methods(org.springframework.web.bind.annotation.RequestMethod.valueOf(method.name()))
        .build();
  }

  private HandlerMethod handler(String methodName) throws NoSuchMethodException {
    Method method = TestController.class.getDeclaredMethod(methodName);
    return new HandlerMethod(new TestController(), method);
  }

  private HandlerMethod operationHandler(String summary, String description) {
    HandlerMethod handlerMethod = mock(HandlerMethod.class);
    Operation operation = mock(Operation.class);
    when(operation.summary()).thenReturn(summary);
    when(operation.description()).thenReturn(description);
    when(handlerMethod.getMethodAnnotation(Operation.class)).thenReturn(operation);
    try {
      when(handlerMethod.getMethod()).thenReturn(TestController.class.getDeclaredMethod("order"));
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException(exception);
    }
    return handlerMethod;
  }

  private void assertInvalidDefinition(String path, String summary, String description) {
    HandlerMethod handlerMethod = operationHandler(summary, description);
    when(handlerMapping.getHandlerMethods())
        .thenReturn(Map.of(mapping(path, HttpMethod.GET), handlerMethod));
    SyncApisRunner runner = new SyncApisRunner(handlerMapping, systemApiService, "sync-apis");

    BusinessException exception =
        assertThrows(BusinessException.class, () -> runner.run(new DefaultApplicationArguments()));

    assertEquals(ExceptionCode.SYSTEM_API_DEFINITION_INVALID, exception.getExceptionCode());
    assertTrue(exception.getMessage().contains("TestController#order"));
    assertTrue(exception.getMessage().contains(path));
    verify(systemApiService, never()).sync(org.mockito.ArgumentMatchers.anyList());
  }

  static class TestController {
    @Operation(summary = "登录", description = "公开登录")
    void login() {}

    @Operation(summary = "当前员工", description = "仅登录访问")
    void current() {}

    @Operation(summary = "查询订单", description = "按订单ID查询订单详情")
    void order() {}

    void withoutOperation() {}
  }
}
