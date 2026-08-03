package com.oa.common.model.system.permission;

import com.oa.common.model.system.vo.ResourceVO;
import java.util.List;
import java.util.Set;

/** 请求内合成的员工权限。 */
public class ResolvedEmployeePermission {
  /** 员工可访问的接口路径。 */
  private Set<String> apiPaths;

  /** 员工可访问的资源树。 */
  private List<ResourceVO> resources;

  public ResolvedEmployeePermission() {}

  public Set<String> getApiPaths() {
    return apiPaths;
  }

  public void setApiPaths(Set<String> apiPaths) {
    this.apiPaths = Set.copyOf(apiPaths);
  }

  public List<ResourceVO> getResources() {
    return resources;
  }

  public void setResources(List<ResourceVO> resources) {
    this.resources = resources;
  }
}
