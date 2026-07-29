package com.oa.action.controller.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 员工管理接口测试。 */
class EmployeeControllerTest {
  /** 员工接口路径应唯一，且都具有OpenAPI说明。 */
  @Test
  void shouldExposeUniqueDocumentedPaths() {
    RequestMapping root = EmployeeController.class.getAnnotation(RequestMapping.class);
    assertThat(root.value()).containsExactly("/api/system/employees");
    Set<String> paths = new HashSet<>();
    for (Method method : EmployeeController.class.getDeclaredMethods()) {
      RequestMapping mapping =
          AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      if (mapping == null) {
        continue;
      }
      assertThat(method.getAnnotation(Operation.class)).isNotNull();
      assertThat(mapping.path()).hasSize(1);
      assertThat(paths.add(mapping.path()[0])).isTrue();
    }
    assertThat(paths)
        .containsExactlyInAnyOrder(
            "/page",
            "/detail",
            "/create",
            "/update",
            "/status",
            "/delete",
            "/reset-password",
            "/roles",
            "/role-ids");
  }

  /** 所有带校验请求的接口都应声明422响应。 */
  @Test
  void validRequestsShouldDocumentUnprocessableEntityResponse() {
    for (Method method : EmployeeController.class.getDeclaredMethods()) {
      boolean hasValidParameter =
          java.util.Arrays.stream(method.getParameters())
              .anyMatch(parameter -> parameter.getAnnotation(Valid.class) != null);
      if (!hasValidParameter) {
        continue;
      }
      assertThat(method.getAnnotationsByType(ApiResponse.class))
          .extracting(ApiResponse::responseCode)
          .contains("422");
    }
  }

  /** Controller和所有原始ID参数应启用正数方法校验。 */
  @Test
  void rawIdParametersShouldBePositive() {
    assertThat(EmployeeController.class.getAnnotation(Validated.class)).isNotNull();
    for (String methodName : java.util.List.of("detail", "delete", "roles", "roleIds")) {
      Method method =
          java.util.Arrays.stream(EmployeeController.class.getDeclaredMethods())
              .filter(candidate -> candidate.getName().equals(methodName))
              .findFirst()
              .orElseThrow();
      java.lang.reflect.Parameter idParameter =
          java.util.Arrays.stream(method.getParameters())
              .filter(parameter -> parameter.getAnnotation(RequestParam.class) != null)
              .findFirst()
              .orElseThrow();
      assertThat(idParameter.getAnnotation(Positive.class)).isNotNull();
      assertThat(method.getAnnotationsByType(ApiResponse.class))
          .extracting(ApiResponse::responseCode)
          .contains("422");
    }
  }
}
