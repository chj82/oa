package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.response.PageQuery;
import jakarta.validation.constraints.Size;

/** 系统接口分页筛选条件。 */
public class SystemApiQueryDTO extends PageQuery {
  /** 接口名称或路径关键字。 */
  @Size(max = 200)
  private String keyword;

  /** 接口状态筛选条件。 */
  private SystemStatus status;

  public SystemApiQueryDTO() {}

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
