package com.oa.common.model.system.cache;

import java.util.Map;
import java.util.Set;

/** 发布到 JVM 的版本化资源权限快照。 */
public class ResourcePermissionSnapshot {
  /** 快照版本。 */
  private long version;

  /** 按资源ID索引的有效资源节点。 */
  private Map<Long, ResourcePermissionNode> nodes;

  /** 全部启用接口路径。 */
  private Set<String> allEnabledApiPaths;

  public ResourcePermissionSnapshot() {}

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public Map<Long, ResourcePermissionNode> getNodes() {
    return nodes;
  }

  public void setNodes(Map<Long, ResourcePermissionNode> nodes) {
    this.nodes = Map.copyOf(nodes);
  }

  public Set<String> getAllEnabledApiPaths() {
    return allEnabledApiPaths;
  }

  public void setAllEnabledApiPaths(Set<String> allEnabledApiPaths) {
    this.allEnabledApiPaths = Set.copyOf(allEnabledApiPaths);
  }
}
