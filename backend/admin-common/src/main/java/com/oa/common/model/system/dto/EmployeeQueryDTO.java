package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.response.PageQuery;

/** 员工分页筛选条件。 */
public class EmployeeQueryDTO extends PageQuery {
  /** 用户名或姓名关键字。 */
  private String keyword;

  /** 部门ID筛选条件。 */
  private Long departmentId;

  /** 状态筛选条件。 */
  private SystemStatus status;

  public EmployeeQueryDTO() {}

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }

  public Long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(Long departmentId) {
    this.departmentId = departmentId;
  }

  public SystemStatus getStatus() {
    return status;
  }

  public void setStatus(SystemStatus status) {
    this.status = status;
  }
}
