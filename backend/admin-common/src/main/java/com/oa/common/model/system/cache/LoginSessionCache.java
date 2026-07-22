package com.oa.common.model.system.cache;

/** Redis 登录会话缓存。 */
public class LoginSessionCache {
  /** 员工ID。 */
  private long employeeId;

  /** 登录用户名。 */
  private String username;

  /** 员工姓名。 */
  private String name;

  /** 是否为超级管理员。 */
  private boolean superuser;

  public LoginSessionCache() {}

  public long getEmployeeId() {
    return employeeId;
  }

  public void setEmployeeId(long employeeId) {
    this.employeeId = employeeId;
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

  public boolean getSuperuser() {
    return superuser;
  }

  public void setSuperuser(boolean superuser) {
    this.superuser = superuser;
  }
}
