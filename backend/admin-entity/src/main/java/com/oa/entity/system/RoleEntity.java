package com.oa.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 角色表实体。 */
@TableName("t_role")
public class RoleEntity {
  /** 角色ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  /** 角色编码。 */
  @TableField("code")
  private String code;

  /** 角色名称。 */
  @TableField("name")
  private String name;

  /** 角色描述。 */
  @TableField("description")
  private String description;

  /** 角色状态。 */
  @TableField("status")
  private int status;

  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;

  public RoleEntity() {}

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

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
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
