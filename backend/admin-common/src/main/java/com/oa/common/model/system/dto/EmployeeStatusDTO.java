package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.NotNull;

/** 员工状态修改请求。 */
public class EmployeeStatusDTO {
  /** 员工ID。 */
  @NotNull private Long id;

  /** 目标员工状态。 */
  @NotNull private SystemStatus status;

  public EmployeeStatusDTO() {}

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
