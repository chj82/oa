package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 系统资源状态修改请求。 */
public class ResourceStatusDTO {
  /** 资源ID。 */
  @NotNull @Positive private Long id;

  /** 资源状态。 */
  @NotNull private SystemStatus status;

  public ResourceStatusDTO() {}

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
