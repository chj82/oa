package com.oa.common.model.system.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 系统资源类型。 */
public enum ResourceType {
  /** 导航目录。 */
  DIRECTORY("DIRECTORY", "目录"),
  /** 页面菜单。 */
  MENU("MENU", "菜单"),
  /** 页面操作按钮。 */
  ACTION("ACTION", "操作");

  /** 数据库存储的资源类型编码。 */
  @EnumValue private final String code;

  /** 界面展示的中文名称。 */
  private final String name;

  ResourceType(String code, String name) {
    this.code = code;
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }
}
