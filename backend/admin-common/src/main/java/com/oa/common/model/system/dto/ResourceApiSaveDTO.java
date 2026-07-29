package com.oa.common.model.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 资源接口关联保存请求。 */
public class ResourceApiSaveDTO {
  /** 资源ID。 */
  @NotNull @Positive private Long resourceId;

  /** 接口ID列表，空列表表示清空。 */
  @NotNull
  @Size(max = 2000)
  private List<@NotNull @Positive Long> apiIds;

  public ResourceApiSaveDTO() {}

  public Long getResourceId() {
    return resourceId;
  }

  public void setResourceId(Long resourceId) {
    this.resourceId = resourceId;
  }

  public List<Long> getApiIds() {
    return apiIds;
  }

  public void setApiIds(List<Long> apiIds) {
    this.apiIds = apiIds;
  }
}
