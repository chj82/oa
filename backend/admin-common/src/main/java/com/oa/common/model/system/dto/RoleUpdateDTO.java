package com.oa.common.model.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 角色修改请求。 */
public class RoleUpdateDTO extends RoleCreateDTO {
  /** 角色ID。 */
  @NotNull @Positive private Long id;

  public RoleUpdateDTO() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }
}
