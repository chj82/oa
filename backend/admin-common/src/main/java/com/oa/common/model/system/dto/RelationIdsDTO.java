package com.oa.common.model.system.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 多对多关联保存请求。 */
public class RelationIdsDTO {
  /** 待保存的关联对象ID列表。 */
  @NotNull private List<Long> ids;

  public RelationIdsDTO() {}

  public List<Long> getIds() {
    return ids;
  }

  public void setIds(List<Long> ids) {
    this.ids = ids;
  }
}
