package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 部门状态修改请求。 */
public class DepartmentStatusDTO {
  /** 部门ID。 */
  @NotNull @Positive private Long id;

  /** 目标部门状态。 */
  @NotNull private SystemStatus status;

  public DepartmentStatusDTO() {}

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
