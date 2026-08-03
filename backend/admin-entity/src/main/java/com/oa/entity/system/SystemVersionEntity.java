package com.oa.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 系统版本实体。 */
@TableName("t_system_version")
public class SystemVersionEntity {
  /** 系统版本ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  /** 版本编码。 */
  @TableField("version_code")
  private String versionCode;

  /** 版本值。 */
  @TableField("version_value")
  private long versionValue;

  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;

  public SystemVersionEntity() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getVersionCode() {
    return versionCode;
  }

  public void setVersionCode(String versionCode) {
    this.versionCode = versionCode;
  }

  public long getVersionValue() {
    return versionValue;
  }

  public void setVersionValue(long versionValue) {
    this.versionValue = versionValue;
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
