package com.oa.common.model.system.dto;

import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 系统资源新增请求。 */
public class ResourceCreateDTO {
  /** 父资源ID，根资源为零。 */
  @NotNull
  @Min(0)
  private Long parentId;

  /** 资源类型。 */
  @NotNull private ResourceType type;

  /** 资源名称。 */
  @NotBlank
  @Size(max = 100)
  private String name;

  /** 菜单或操作资源编码。 */
  @Size(max = 100)
  private String code;

  /** 前端菜单路由。 */
  @Size(max = 255)
  private String path;

  /** 图标名称。 */
  @Size(max = 100)
  private String icon;

  /** 排序值，越小越靠前。 */
  @Min(0)
  @Max(1000000)
  private int sortOrder;

  /** 是否在导航中可见。 */
  private boolean visible;

  /** 资源状态。 */
  @NotNull private SystemStatus status;

  public ResourceCreateDTO() {}

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public ResourceType getType() {
    return type;
  }

  public void setType(ResourceType type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public boolean getVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public SystemStatus getStatus() {
    return status;
  }

  public void setStatus(SystemStatus status) {
    this.status = status;
  }
}
