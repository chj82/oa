package com.oa.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 部门表实体。 */
@TableName("t_department")
public class DepartmentEntity {
  /** 部门ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  /** 父部门ID，根部门为零。 */
  @TableField("parent_id")
  private long parentId;

  /** 部门名称。 */
  @TableField("name")
  private String name;

  /** 排序值，越小越靠前。 */
  @TableField("sort_order")
  private int sortOrder;

  /** 部门状态。 */
  @TableField("status")
  private int status;

  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;

  public DepartmentEntity() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
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
