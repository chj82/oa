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

/** 系统接口目录控制器契约测试。 */
class SystemApiControllerTest {
  @Test
  void 仅暴露分页详情和启停接口且具有OpenAPI说明() {
    assertThat(SystemApiController.class.getAnnotation(Validated.class)).isNotNull();
    RequestMapping root = SystemApiController.class.getAnnotation(RequestMapping.class);
    assertThat(root.value()).containsExactly("/api/system/apis");
    Set<String> paths = new HashSet<>();
    for (Method method : SystemApiController.class.getDeclaredMethods()) {
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
    assertThat(paths).containsExactlyInAnyOrder("/page", "/detail", "/status");
  }
}
