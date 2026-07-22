package com.oa.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 角色资源关联表实体。 */
@TableName("t_role_resource")
public class RoleResourceEntity {
  /** 角色资源关联ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private long id;

  /** 角色ID。 */
  @TableField("role_id")
  private long roleId;

  /** 资源ID。 */
  @TableField("resource_id")
  private long resourceId;

  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  public RoleResourceEntity() {}

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getRoleId() {
    return roleId;
  }

  public void setRoleId(long roleId) {
    this.roleId = roleId;
  }

  public long getResourceId() {
    return resourceId;
  }

  public void setResourceId(long resourceId) {
    this.resourceId = resourceId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
