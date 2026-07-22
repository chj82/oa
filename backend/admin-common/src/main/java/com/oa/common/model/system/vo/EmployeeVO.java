package com.oa.common.model.system.vo;

import com.oa.common.model.system.enums.SystemStatus;
import java.time.LocalDateTime;

/** 员工展示模型。 */
public class EmployeeVO {
  /** 员工ID。 */
  private Long id;

  /** 登录用户名。 */
  private String username;

  /** 员工姓名。 */
  private String name;

  /** 手机号。 */
  private String phone;

  /** 邮箱。 */
  private String email;

  /** 所属部门ID。 */
  private Long departmentId;

  /** 员工状态。 */
  private SystemStatus status;

  /** 是否为超级管理员。 */
  private boolean superuser;

  /** 创建时间。 */
  private LocalDateTime createdAt;

  /** 更新时间。 */
  private LocalDateTime updatedAt;

  public EmployeeVO() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
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

  public Long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(Long departmentId) {
    this.departmentId = departmentId;
  }

  public SystemStatus getStatus() {
    return status;
  }

  public void setStatus(SystemStatus status) {
    this.status = status;
  }

  public boolean getSuperuser() {
    return superuser;
  }

  public void setSuperuser(boolean superuser) {
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
