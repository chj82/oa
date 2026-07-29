package com.oa.common.model.system.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.Validation;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

/** 角色写请求校验测试。 */
class RoleWriteDTOTest {
  /** 新增请求应校验长度、必填字段和状态。 */
  @Test
  void shouldValidateCreateFields() {
    RoleCreateDTO request = new RoleCreateDTO();
    request.setCode("x".repeat(65));
    request.setName("x".repeat(101));
    request.setDescription("x".repeat(501));
    assertTrue(fields(request).containsAll(List.of("code", "name", "description", "status")));
  }

  /** 修改与状态请求ID必须为正数。 */
  @Test
  void shouldRejectNonPositiveIds() {
    RoleUpdateDTO update = new RoleUpdateDTO();
    update.setId(0L);
    update.setCode("code");
    update.setName("角色");
    update.setStatus(SystemStatus.ENABLED);
    RoleStatusDTO status = new RoleStatusDTO();
    status.setId(-1L);
    status.setStatus(SystemStatus.DISABLED);
    assertTrue(fields(update).contains("id"));
    assertTrue(fields(status).contains("id"));
  }

  /** 角色资源列表应拒绝空值、非正数和超过上限。 */
  @Test
  void shouldValidateResourceIds() {
    RoleResourceSaveDTO request = new RoleResourceSaveDTO();
    request.setIds(null);
    assertTrue(fields(request).contains("ids"));
    request.setIds(java.util.Arrays.asList(1L, null, 0L));
    assertTrue(
        fields(request).stream().filter(field -> field.contains("list element")).count() == 2);
    request.setIds(new ArrayList<>(java.util.Collections.nCopies(2001, 1L)));
    assertTrue(fields(request).contains("ids"));
  }

  private List<String> fields(Object request) {
    try (var factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      return factory.getValidator().validate(request).stream()
          .map(item -> item.getPropertyPath().toString())
          .toList();
    }
  }
}
