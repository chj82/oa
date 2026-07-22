package com.oa.common.model.system.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.annotation.EnumValue;
import org.junit.jupiter.api.Test;

/** 系统状态枚举测试。 */
class SystemStatusTest {

  /** 状态值必须与数据库约定一致。 */
  @Test
  void shouldMatchDatabaseStatusValues() {
    assertEquals(0, SystemStatus.DISABLED.getCode());
    assertEquals("禁用", SystemStatus.DISABLED.getName());
    assertEquals(1, SystemStatus.ENABLED.getCode());
    assertEquals("启用", SystemStatus.ENABLED.getName());
    assertNotNull(enumCodeAnnotation(SystemStatus.class));
  }

  /** 资源类型必须覆盖设计约定的三种类型。 */
  @Test
  void shouldContainDesignedResourceTypes() {
    assertEquals(3, ResourceType.values().length);
    assertEquals(ResourceType.DIRECTORY, ResourceType.valueOf("DIRECTORY"));
    assertEquals(ResourceType.MENU, ResourceType.valueOf("MENU"));
    assertEquals(ResourceType.ACTION, ResourceType.valueOf("ACTION"));
    assertEquals("DIRECTORY", ResourceType.DIRECTORY.getCode());
    assertEquals("目录", ResourceType.DIRECTORY.getName());
    assertEquals("MENU", ResourceType.MENU.getCode());
    assertEquals("菜单", ResourceType.MENU.getName());
    assertEquals("ACTION", ResourceType.ACTION.getCode());
    assertEquals("操作", ResourceType.ACTION.getName());
    assertNotNull(enumCodeAnnotation(ResourceType.class));
  }

  private EnumValue enumCodeAnnotation(Class<?> type) {
    try {
      return type.getDeclaredField("code").getAnnotation(EnumValue.class);
    } catch (NoSuchFieldException exception) {
      throw new AssertionError(exception);
    }
  }
}
