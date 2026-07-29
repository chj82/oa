package com.oa.common.model.system.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

/** 部门写请求校验测试。 */
class DepartmentWriteDTOTest {
  /** 新增请求应校验父ID、名称和状态。 */
  @Test
  void shouldValidateCreateFields() {
    DepartmentCreateDTO request = new DepartmentCreateDTO();
    request.setParentId(-1L);
    request.setName(" ");
    try (var factory = validatorFactory()) {
      var fields =
          factory.getValidator().validate(request).stream()
              .map(item -> item.getPropertyPath().toString())
              .toList();
      assertTrue(fields.containsAll(java.util.List.of("parentId", "name", "status")));
    }
  }

  /** 修改请求必须提供部门ID。 */
  @Test
  void shouldRequireUpdateId() {
    DepartmentUpdateDTO request = new DepartmentUpdateDTO();
    request.setParentId(0L);
    request.setName("研发部");
    try (var factory = validatorFactory()) {
      var fields =
          factory.getValidator().validate(request).stream()
              .map(item -> item.getPropertyPath().toString())
              .toList();
      assertTrue(fields.containsAll(java.util.List.of("id", "status")));
    }
  }

  /** 修改和状态请求的ID必须为正数。 */
  @Test
  void shouldRejectNonPositiveIds() {
    DepartmentUpdateDTO update = new DepartmentUpdateDTO();
    update.setId(0L);
    update.setParentId(-1L);
    update.setName("研发部");
    update.setStatus(com.oa.common.model.system.enums.SystemStatus.ENABLED);
    DepartmentStatusDTO status = new DepartmentStatusDTO();
    status.setId(-1L);
    status.setStatus(com.oa.common.model.system.enums.SystemStatus.DISABLED);

    try (var factory = validatorFactory()) {
      assertTrue(
          factory.getValidator().validate(update).stream()
              .anyMatch(item -> item.getPropertyPath().toString().equals("id")));
      assertTrue(
          factory.getValidator().validate(update).stream()
              .anyMatch(item -> item.getPropertyPath().toString().equals("parentId")));
      assertTrue(
          factory.getValidator().validate(status).stream()
              .anyMatch(item -> item.getPropertyPath().toString().equals("id")));
    }
  }

  private jakarta.validation.ValidatorFactory validatorFactory() {
    return Validation.byDefaultProvider()
        .configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .buildValidatorFactory();
  }
}
