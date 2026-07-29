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

/** 系统资源接口契约测试。 */
class ResourceControllerTest {
  /** 资源接口路径应唯一、完整并声明OpenAPI与422响应。 */
  @Test
  void shouldExposeDocumentedResourcePaths() {
    assertThat(ResourceController.class.getAnnotation(Validated.class)).isNotNull();
    assertThat(ResourceController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/system/resources");
    Set<String> paths = new HashSet<>();
    for (Method method : ResourceController.class.getDeclaredMethods()) {
      RequestMapping mapping =
          AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      if (mapping == null) {
        continue;
      }
      assertThat(method.getAnnotation(Operation.class)).isNotNull();
      assertThat(method.getAnnotationsByType(ApiResponse.class))
          .extracting(ApiResponse::responseCode)
          .contains("422");
      assertThat(paths.add(mapping.path()[0])).isTrue();
    }
    assertThat(paths)
        .containsExactlyInAnyOrder(
            "/tree", "/detail", "/create", "/update", "/status", "/delete", "/api-ids", "/apis");
  }
}
