package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 系统接口状态修改参数。 */
public class SystemApiStatusDTO {
  /** 接口ID。 */
  @NotNull @Positive private Long id;

  /** 目标接口状态。 */
  @NotNull private SystemStatus status;

  public SystemApiStatusDTO() {}

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
