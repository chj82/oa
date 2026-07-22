package com.oa.common.model.system.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 系统通用启停状态。 */
public enum SystemStatus {
  /** 禁用状态。 */
  DISABLED(0, "禁用"),
  /** 启用状态。 */
  ENABLED(1, "启用");

  /** 数据库存储的状态编码。 */
  @EnumValue private final Integer code;

  /** 界面展示的中文名称。 */
  private final String name;

  SystemStatus(Integer code, String name) {
    this.code = code;
    this.name = name;
  }

  public Integer getCode() {
    return code;
  }

  public String getName() {
    return name;
  }
}
