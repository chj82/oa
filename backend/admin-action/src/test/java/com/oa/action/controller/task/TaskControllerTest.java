package com.oa.action.controller.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

/** 任务管理接口契约测试。 */
class TaskControllerTest {

  /** 任务管理必须提供与paas-core一致的列表和手动运行路径。 */
  @Test
  void 暴露任务列表和运行接口() {
    RequestMapping root = TaskController.class.getAnnotation(RequestMapping.class);

    assertThat(root.value()).containsExactly("/api/admin/task");
    assertThat(
            Arrays.stream(TaskController.class.getDeclaredMethods())
                .map(method -> mappingPath(method))
                .filter(path -> path != null)
                .toList())
        .containsExactlyInAnyOrder("/list.htm", "/run.htm");
  }

  private String mappingPath(Method method) {
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
    return mapping == null ? null : mapping.path()[0];
  }
}
