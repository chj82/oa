package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 部门修改请求。 */
public class DepartmentUpdateDTO {
  /** 部门ID。 */
  @NotNull @Positive private Long id;

  /** 父部门ID，根部门为零。 */
  @NotNull @PositiveOrZero private Long parentId;

  /** 部门名称。 */
  @NotBlank
  @Size(max = 100)
  private String name;

  /** 排序值，越小越靠前。 */
  @PositiveOrZero
  @Max(999999)
  private int sortOrder;

  /** 部门状态。 */
  @NotNull private SystemStatus status;

  public DepartmentUpdateDTO() {}

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
