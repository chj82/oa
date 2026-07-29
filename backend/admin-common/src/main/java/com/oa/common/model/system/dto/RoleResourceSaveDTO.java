package com.oa.common.model.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 角色资源保存请求。 */
public class RoleResourceSaveDTO {
  /** 实际选中的资源ID列表。 */
  @NotNull
  @Size(max = 2000)
  private List<@NotNull @Positive Long> ids;

  public RoleResourceSaveDTO() {}

  public List<Long> getIds() {
    return ids;
  }

  public void setIds(List<Long> ids) {
    this.ids = ids;
  }
}
