package com.oa.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 系统资源表实体。 */
@TableName("t_system_resource")
public class SystemResourceEntity {
  /** 资源ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private long id;

  /** 父资源ID，根资源为零。 */
  @TableField("parent_id")
  private long parentId;

  /** 资源类型。 */
  @TableField("type")
  private String type;

  /** 资源名称。 */
  @TableField("name")
  private String name;

  /** 菜单或操作资源编码。 */
  @TableField("code")
  private String code;

  /** 前端菜单路由。 */
  @TableField("path")
  private String path;

  /** 图标名称。 */
  @TableField("icon")
  private String icon;

  /** 排序值，越小越靠前。 */
  @TableField("sort_order")
  private int sortOrder;

  /** 是否在导航中可见。 */
  @TableField("visible")
  private int visible;

  /** 资源状态。 */
  @TableField("status")
  private int status;

  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;

  public SystemResourceEntity() {}

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

  public int getVisible() {
    return visible;
  }

  public void setVisible(int visible) {
    this.visible = visible;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
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
