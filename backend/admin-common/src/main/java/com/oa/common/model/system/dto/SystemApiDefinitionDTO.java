package com.oa.common.model.system.dto;

/** 从 Spring MVC Handler 扫描得到的系统接口定义。 */
public class SystemApiDefinitionDTO {
  /** 接口名称，来源于 OpenAPI Operation 摘要。 */
  private String name;

  /** Spring MVC 路由模板。 */
  private String path;

  /** 接口描述，来源于 OpenAPI Operation 描述。 */
  private String description;

  public SystemApiDefinitionDTO() {}

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
}
