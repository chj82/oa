package com.oa.common.model.system.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

/** 员工写请求模型边界测试。 */
class EmployeeWriteDTOTest {
  /** 新增模型不允许客户端提交ID，且密码必须填写。 */
  @Test
  void createModelShouldHaveRequiredPasswordWithoutId() throws Exception {
    Set<String> fields = fieldNames(EmployeeCreateDTO.class);
    assertFalse(fields.contains("id"));
    assertTrue(fields.contains("password"));
    assertNotNull(
        EmployeeCreateDTO.class.getDeclaredField("password").getAnnotation(NotBlank.class));
  }

  /** 修改模型必须提交ID，且不得提供密码字段。 */
  @Test
  void updateModelShouldHaveRequiredIdWithoutPassword() throws Exception {
    Set<String> fields = fieldNames(EmployeeUpdateDTO.class);
    assertTrue(fields.contains("id"));
    assertFalse(fields.contains("password"));
    assertNotNull(EmployeeUpdateDTO.class.getDeclaredField("id").getAnnotation(NotNull.class));
  }

  /** 员工字段超过数据库列宽时校验失败。 */
  @Test
  void employeeFieldsShouldRespectDatabaseColumnLengths() {
    EmployeeCreateDTO request = new EmployeeCreateDTO();
    request.setUsername("u".repeat(65));
    request.setName("n".repeat(101));
    request.setPassword("p".repeat(73));
    request.setPhone("1".repeat(33));
    request.setEmail("a".repeat(246) + "@example.com");

    assertTrue(validationCount(request) >= 5);
  }

  /** 关联ID列表最多200项且元素不能为空。 */
  @Test
  void relationIdsShouldHaveCountAndElementConstraints() {
    RelationIdsDTO tooMany = new RelationIdsDTO();
    tooMany.setIds(java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList());
    RelationIdsDTO nullElement = new RelationIdsDTO();
    nullElement.setIds(List.of(1L, 2L, 3L));
    java.util.ArrayList<Long> ids = new java.util.ArrayList<>(nullElement.getIds());
    ids.add(null);
    nullElement.setIds(ids);

    assertTrue(validationCount(tooMany) >= 1);
    assertTrue(validationCount(nullElement) >= 1);
  }

  private int validationCount(Object value) {
    try (var factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      return factory.getValidator().validate(value).size();
    }
  }

  private Set<String> fieldNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .map(java.lang.reflect.Field::getName)
        .collect(Collectors.toSet());
  }
}
