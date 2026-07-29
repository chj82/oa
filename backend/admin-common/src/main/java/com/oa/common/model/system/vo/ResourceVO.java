package com.oa.common.model.system.vo;

import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 系统资源展示模型。 */
public class ResourceVO {
  /** 资源ID。 */
  private Long id;

  /** 父资源ID，根资源为零。 */
  private Long parentId;

  /** 资源类型。 */
  private ResourceType type;

  /** 资源名称。 */
  private String name;

  /** 菜单或操作资源编码。 */
  private String code;

  /** 前端菜单路由。 */
  private String path;

  /** 图标名称。 */
  private String icon;

  /** 排序值，越小越靠前。 */
  private int sortOrder;

  /** 是否在导航中可见。 */
  private boolean visible;

  /** 资源状态。 */
  private SystemStatus status;

  /** 子资源列表。 */
  private List<ResourceVO> children;

  /** 创建时间。 */
  private LocalDateTime createdAt;

  /** 更新时间。 */
  private LocalDateTime updatedAt;

  public ResourceVO() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public ResourceType getType() {
    return type;
  }

  public void setType(ResourceType type) {
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

  public SystemStatus getStatus() {
    return status;
  }

  public void setStatus(SystemStatus status) {
    this.status = status;
  }

  public List<ResourceVO> getChildren() {
    return children;
  }

  public void setChildren(List<ResourceVO> children) {
    this.children = children;
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
