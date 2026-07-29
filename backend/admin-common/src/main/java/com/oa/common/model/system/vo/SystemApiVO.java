package com.oa.common.model.system.vo;

import com.oa.common.model.system.enums.SystemStatus;
import java.time.LocalDateTime;

/** 系统接口展示模型。 */
public class SystemApiVO {
  /** 接口ID。 */
  private Long id;

  /** 接口名称。 */
  private String name;

  /** Spring MVC 路由模板。 */
  private String path;

  /** 接口描述。 */
  private String description;

  /** 接口状态。 */
  private SystemStatus status;

  /** 创建时间。 */
  private LocalDateTime createdAt;

  /** 更新时间。 */
  private LocalDateTime updatedAt;

  public SystemApiVO() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
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
