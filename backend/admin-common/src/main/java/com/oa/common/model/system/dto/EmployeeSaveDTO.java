package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 员工新增或修改请求。 */
public class EmployeeSaveDTO {
  /** 员工ID，新增时为空。 */
  private Long id;

  /** 登录用户名。 */
  @NotBlank private String username;

  /** 员工姓名。 */
  @NotBlank private String name;

  /** 登录密码，修改且不重置密码时为空。 */
  private String password;

  /** 手机号。 */
  private String phone;

  /** 邮箱。 */
  @Email private String email;

  /** 所属部门ID。 */
  @NotNull private Long departmentId;

  /** 员工状态。 */
  @NotNull private SystemStatus status;

  /** 是否为超级管理员。 */
  private boolean superuser;

  public EmployeeSaveDTO() {}

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

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
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
}
