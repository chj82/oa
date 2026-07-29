package com.oa.action.controller.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;

/** 角色管理接口测试。 */
class RoleControllerTest {
  /** 角色接口路径应唯一并具有OpenAPI说明。 */
  @Test
  void shouldExposeUniqueDocumentedPaths() {
    assertThat(RoleController.class.getAnnotation(Validated.class)).isNotNull();
    RequestMapping root = RoleController.class.getAnnotation(RequestMapping.class);
    assertThat(root.value()).containsExactly("/api/system/roles");
    Set<String> paths = new HashSet<>();
    for (Method method : RoleController.class.getDeclaredMethods()) {
      RequestMapping mapping =
          AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      if (mapping == null) {
        continue;
      }
      assertThat(method.getAnnotation(Operation.class)).isNotNull();
      assertThat(paths.add(mapping.path()[0])).isTrue();
      assertThat(method.getAnnotationsByType(ApiResponse.class))
          .extracting(ApiResponse::responseCode)
          .contains("422");
    }
    assertThat(paths)
        .containsExactlyInAnyOrder(
            "/page",
            "/detail",
            "/resource-ids",
            "/create",
            "/update",
            "/status",
            "/delete",
            "/resources");
  }
}
