package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 角色新增或修改请求。 */
public class RoleSaveDTO {
  /** 角色ID，新增时为空。 */
  private Long id;

  /** 角色编码。 */
  @NotBlank private String code;

  /** 角色名称。 */
  @NotBlank private String name;

  /** 角色描述。 */
  private String description;

  /** 角色状态。 */
  @NotNull private SystemStatus status;

  public RoleSaveDTO() {}

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
