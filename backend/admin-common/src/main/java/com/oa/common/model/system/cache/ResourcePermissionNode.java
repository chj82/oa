package com.oa.common.model.system.cache;

import java.time.LocalDateTime;
import java.util.Set;

/** JVM 资源权限快照节点。 */
public class ResourcePermissionNode {
  /** 资源ID。 */
  private long id;

  /** 父资源ID。 */
  private long parentId;

  /** 资源类型编码。 */
  private String type;

  /** 资源名称。 */
  private String name;

  /** 资源编码。 */
  private String code;

  /** 前端菜单路由。 */
  private String path;

  /** 图标名称。 */
  private String icon;

  /** 排序值。 */
  private int sortOrder;

  /** 是否在导航中可见。 */
  private boolean visible;

  /** 资源状态。 */
  private int status;

  /** 资源关联的启用接口路径。 */
  private Set<String> apiPaths;

  /** 创建时间。 */
  private LocalDateTime createdAt;

  /** 更新时间。 */
  private LocalDateTime updatedAt;

  public ResourcePermissionNode() {}

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public boolean getVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public Set<String> getApiPaths() {
    return apiPaths;
  }

  public void setApiPaths(Set<String> apiPaths) {
    this.apiPaths = Set.copyOf(apiPaths);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
