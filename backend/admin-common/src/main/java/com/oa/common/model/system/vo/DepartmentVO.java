package com.oa.common.model.system.vo;

import com.oa.common.model.system.enums.SystemStatus;

/** 部门展示模型。 */
public class DepartmentVO {
  /** 部门ID。 */
  private Long id;

  /** 父部门ID，根部门为零。 */
  private Long parentId;

  /** 部门名称。 */
  private String name;

  /** 排序值，越小越靠前。 */
  private int sortOrder;

  /** 部门状态。 */
  private SystemStatus status;

  public DepartmentVO() {}

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

  public SystemStatus getStatus() {
    return status;
  }

  public void setStatus(SystemStatus status) {
    this.status = status;
  }
}
