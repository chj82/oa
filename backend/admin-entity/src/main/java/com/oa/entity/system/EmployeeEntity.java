package com.oa.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 员工表实体。 */
@TableName("t_employee")
public class EmployeeEntity {
  /** 员工ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private long id;

  /** 登录用户名。 */
  @TableField("username")
  private String username;

  /** 员工姓名。 */
  @TableField("name")
  private String name;

  /** BCrypt密码哈希。 */
  @TableField("password_hash")
  private String passwordHash;

  /** 手机号。 */
  @TableField("phone")
  private String phone;

  /** 邮箱。 */
  @TableField("email")
  private String email;

  /** 所属部门ID。 */
  @TableField("department_id")
  private long departmentId;

  /** 员工状态。 */
  @TableField("status")
  private int status;

  /** 是否为超级管理员。 */
  @TableField("is_superuser")
  private int superuser;

  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;

  public EmployeeEntity() {}

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(long departmentId) {
    this.departmentId = departmentId;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public int getSuperuser() {
    return superuser;
  }

  public void setSuperuser(int superuser) {
    this.superuser = superuser;
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
