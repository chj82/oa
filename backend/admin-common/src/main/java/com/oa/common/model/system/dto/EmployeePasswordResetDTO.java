package com.oa.common.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 员工密码重置请求。 */
public class EmployeePasswordResetDTO {
  /** 员工ID。 */
  @NotNull private Long id;

  /** 重置后的登录密码。 */
  @NotBlank
  @Size(min = 8, max = 72)
  private String password;

  public EmployeePasswordResetDTO() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
