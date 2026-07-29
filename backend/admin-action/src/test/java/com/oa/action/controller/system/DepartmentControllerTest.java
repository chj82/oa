package com.oa.action.controller.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

/** 部门管理接口测试。 */
class DepartmentControllerTest {
  /** 部门接口路径应唯一、单Method且具有OpenAPI说明。 */
  @Test
  void shouldExposeUniqueDocumentedPaths() {
    assertThat(DepartmentController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/system/departments");
    Set<String> paths = new HashSet<>();
    for (Method method : DepartmentController.class.getDeclaredMethods()) {
      RequestMapping mapping =
          AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      if (mapping == null) {
        continue;
      }
      assertThat(method.getAnnotation(Operation.class)).isNotNull();
      assertThat(mapping.path()).hasSize(1);
      assertThat(mapping.method()).hasSize(1);
      assertThat(paths.add(mapping.path()[0])).isTrue();
    }
    assertThat(paths)
        .containsExactlyInAnyOrder("/tree", "/detail", "/create", "/update", "/status", "/delete");
  }

  /** 带校验请求的接口应声明422响应。 */
  @Test
  void validRequestsShouldDocumentUnprocessableEntityResponse() {
    for (Method method : DepartmentController.class.getDeclaredMethods()) {
      boolean validated =
          java.util.Arrays.stream(method.getParameters())
              .anyMatch(parameter -> parameter.getAnnotation(Valid.class) != null);
      if (validated) {
        assertThat(method.getAnnotationsByType(ApiResponse.class))
            .extracting(ApiResponse::responseCode)
            .contains("422");
      }
    }
  }
}
