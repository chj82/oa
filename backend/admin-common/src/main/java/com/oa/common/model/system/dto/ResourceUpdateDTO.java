package com.oa.common.model.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 系统资源修改请求。 */
public class ResourceUpdateDTO extends ResourceCreateDTO {
  /** 资源ID。 */
  @NotNull @Positive private Long id;

  public ResourceUpdateDTO() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }
}
