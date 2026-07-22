package com.oa.common.model.system.vo;

import com.oa.common.model.system.enums.SystemStatus;

/** 角色展示模型。 */
public class RoleVO {
  /** 角色ID。 */
  private Long id;

  /** 角色编码。 */
  private String code;

  /** 角色名称。 */
  private String name;

  /** 角色描述。 */
  private String description;

  /** 角色状态。 */
  private SystemStatus status;

  public RoleVO() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public SystemStatus getStatus() {
    return status;
  }

  public void setStatus(SystemStatus status) {
    this.status = status;
  }
}
