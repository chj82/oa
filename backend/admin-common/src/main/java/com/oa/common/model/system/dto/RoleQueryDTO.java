package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.response.PageQuery;
import jakarta.validation.constraints.Size;

/** 角色分页筛选条件。 */
public class RoleQueryDTO extends PageQuery {
  /** 角色编码或名称关键字。 */
  @Size(max = 100)
  private String keyword;

  /** 角色状态筛选条件。 */
  private SystemStatus status;

  public RoleQueryDTO() {}

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }

  public SystemStatus getStatus() {
    return status;
  }

  public void setStatus(SystemStatus status) {
    this.status = status;
  }
}
