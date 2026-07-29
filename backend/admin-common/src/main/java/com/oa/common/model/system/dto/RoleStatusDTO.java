package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 角色状态修改请求。 */
public class RoleStatusDTO {
  /** 角色ID。 */
  @NotNull @Positive private Long id;

  /** 目标角色状态。 */
  @NotNull private SystemStatus status;

  public RoleStatusDTO() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public SystemStatus getStatus() {
    return status;
  }

  public void setStatus(SystemStatus status) {
    this.status = status;
  }
}
