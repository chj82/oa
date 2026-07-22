package com.oa.common.model.system.vo;

import java.util.List;

/** 当前登录员工及有效资源。 */
public class CurrentEmployeeVO {
  /** 当前员工ID。 */
  private Long id;

  /** 当前员工登录用户名。 */
  private String username;

  /** 当前员工姓名。 */
  private String name;

  /** 是否为超级管理员。 */
  private boolean superuser;

  /** 当前员工有效资源列表。 */
  private List<ResourceVO> resources;

  public CurrentEmployeeVO() {}

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

  public boolean getSuperuser() {
    return superuser;
  }

  public void setSuperuser(boolean superuser) {
    this.superuser = superuser;
  }

  public List<ResourceVO> getResources() {
    return resources;
  }

  public void setResources(List<ResourceVO> resources) {
    this.resources = resources;
  }
}
