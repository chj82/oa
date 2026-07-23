package com.oa.action.controller.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

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
            "/roles");
  }
}
