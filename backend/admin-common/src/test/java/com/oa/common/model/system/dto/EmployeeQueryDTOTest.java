package com.oa.common.model.system.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

/** 员工查询参数校验测试。 */
class EmployeeQueryDTOTest {

  /** 页码和每页记录数必须满足分页模型约束。 */
  @Test
  void shouldRejectZeroPageAndSize() {
    EmployeeQueryDTO query = new EmployeeQueryDTO();
    query.setPage(0);
    query.setSize(0);
    try (var factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      assertEquals(2, factory.getValidator().validate(query).size());
    }
  }
}
