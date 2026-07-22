package com.oa.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 资源接口关联表实体。 */
@TableName("t_resource_api")
public class ResourceApiEntity {
  /** 资源接口关联ID。 */
  @TableId(value = "id", type = IdType.AUTO)
  private long id;

  /** 资源ID。 */
  @TableField("resource_id")
  private long resourceId;

  /** 接口ID。 */
  @TableField("api_id")
  private long apiId;

  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  public ResourceApiEntity() {}

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getResourceId() {
    return resourceId;
  }

  public void setResourceId(long resourceId) {
    this.resourceId = resourceId;
  }

  public long getApiId() {
    return apiId;
  }

  public void setApiId(long apiId) {
    this.apiId = apiId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
