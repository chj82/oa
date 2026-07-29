package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 角色新增请求。 */
public class RoleCreateDTO {
  /** 角色编码。 */
  @NotBlank
  @Size(max = 64)
  private String code;

  /** 角色名称。 */
  @NotBlank
  @Size(max = 100)
  private String name;

  /** 角色描述。 */
  @Size(max = 500)
  private String description;

  /** 角色状态。 */
  @NotNull private SystemStatus status;

  public RoleCreateDTO() {}

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
