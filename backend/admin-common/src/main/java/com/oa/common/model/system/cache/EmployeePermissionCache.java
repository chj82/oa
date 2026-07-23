package com.oa.common.model.system.cache;

import java.util.Set;

/** 员工接口权限缓存。 */
public class EmployeePermissionCache {
  /** 缓存生成时的全局权限版本。 */
  private long version;

  /** 员工可访问的启用接口路由模板集合。 */
  private Set<String> apiPaths;

  public EmployeePermissionCache() {}

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public Set<String> getApiPaths() {
    return apiPaths;
  }

  public void setApiPaths(Set<String> apiPaths) {
    this.apiPaths = apiPaths;
  }
}
