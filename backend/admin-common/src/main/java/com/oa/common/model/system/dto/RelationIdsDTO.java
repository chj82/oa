package com.oa.common.model.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 多对多关联保存请求。 */
public class RelationIdsDTO {
  /** 待保存的关联对象ID列表。 */
  @NotNull
  @Size(max = 200)
  private List<@NotNull @Positive Long> ids;

  public RelationIdsDTO() {}

  public List<Long> getIds() {
    return ids;
  }

  public void setIds(List<Long> ids) {
    this.ids = ids;
  }
}
