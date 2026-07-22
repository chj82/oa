package com.oa.common.model.system.dto;

import jakarta.validation.constraints.NotBlank;

/** 登录请求。 */
public class LoginDTO {
  /** 登录用户名。 */
  @NotBlank private String username;

  /** 登录密码明文，仅用于本次认证。 */
  @NotBlank private String password;

  public LoginDTO() {}

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
