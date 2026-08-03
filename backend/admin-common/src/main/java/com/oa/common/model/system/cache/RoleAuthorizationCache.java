package com.oa.common.model.system.cache;

import java.util.List;

/** 角色授权关系缓存。 */
public class RoleAuthorizationCache {
  /** 角色ID。 */
  private long roleId;

  /** 角色状态。 */
  private int status;

  /** 角色绑定的资源ID集合。 */
  private List<Long> resourceIds;

  public RoleAuthorizationCache() {}

  public long getRoleId() {
    return roleId;
  }

  public void setRoleId(long roleId) {
    this.roleId = roleId;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public List<Long> getResourceIds() {
    return resourceIds;
  }

  public void setResourceIds(List<Long> resourceIds) {
    this.resourceIds = resourceIds;
  }
}
