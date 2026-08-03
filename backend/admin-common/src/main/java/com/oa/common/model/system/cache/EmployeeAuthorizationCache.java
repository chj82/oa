package com.oa.common.model.system.cache;

import java.util.List;

/** 员工授权关系缓存。 */
public class EmployeeAuthorizationCache {
  /** 员工ID。 */
  private long employeeId;

  /** 员工状态。 */
  private int status;

  /** 是否为超级管理员。 */
  private boolean superuser;

  /** 员工绑定的角色ID集合。 */
  private List<Long> roleIds;

  public EmployeeAuthorizationCache() {}

  public long getEmployeeId() {
    return employeeId;
  }

  public void setEmployeeId(long employeeId) {
    this.employeeId = employeeId;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public boolean getSuperuser() {
    return superuser;
  }

  public void setSuperuser(boolean superuser) {
    this.superuser = superuser;
  }

  public List<Long> getRoleIds() {
    return roleIds;
  }

  public void setRoleIds(List<Long> roleIds) {
    this.roleIds = roleIds;
  }
}
