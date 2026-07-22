package com.oa.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 员工角色关联表实体。 */
@TableName("t_employee_role")
public class EmployeeRoleEntity {
  /** 员工角色关联ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private long id;

  /** 员工ID。 */
  @TableField("employee_id")
  private long employeeId;

  /** 角色ID。 */
  @TableField("role_id")
  private long roleId;

  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  public EmployeeRoleEntity() {}

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getEmployeeId() {
    return employeeId;
  }

  public void setEmployeeId(long employeeId) {
    this.employeeId = employeeId;
  }

  public long getRoleId() {
    return roleId;
  }

  public void setRoleId(long roleId) {
    this.roleId = roleId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
