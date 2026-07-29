package com.oa.entity.system;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.system.dto.*;
import com.oa.common.model.system.enums.*;
import com.oa.common.model.system.vo.*;
import com.oa.common.response.ApiResult;
import com.oa.common.response.PageQuery;
import com.oa.common.response.PageResult;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 任务二对象字段中文注释规范测试。 */
class ChineseFieldCommentTest {

  /** 所有实例字段声明前必须具有中文 Javadoc。 */
  @Test
  void shouldDocumentEveryInstanceFieldInChinese() throws Exception {
    List<Class<?>> types =
        List.of(
            BusinessException.class,
            ApiResult.class,
            PageQuery.class,
            PageResult.class,
            SystemStatus.class,
            ResourceType.class,
            LoginDTO.class,
            EmployeeCreateDTO.class,
            EmployeeUpdateDTO.class,
            EmployeeQueryDTO.class,
            DepartmentCreateDTO.class,
            DepartmentUpdateDTO.class,
            DepartmentStatusDTO.class,
            RoleCreateDTO.class,
            RoleUpdateDTO.class,
            RoleStatusDTO.class,
            RoleQueryDTO.class,
            RoleResourceSaveDTO.class,
            ResourceCreateDTO.class,
            ResourceUpdateDTO.class,
            ResourceStatusDTO.class,
            ResourceApiSaveDTO.class,
            RelationIdsDTO.class,
            SystemApiDefinitionDTO.class,
            EmployeeVO.class,
            DepartmentVO.class,
            RoleVO.class,
            ResourceVO.class,
            SystemApiVO.class,
            CurrentEmployeeVO.class,
            DepartmentEntity.class,
            EmployeeEntity.class,
            RoleEntity.class,
            SystemResourceEntity.class,
            SystemApiEntity.class,
            EmployeeRoleEntity.class,
            RoleResourceEntity.class,
            ResourceApiEntity.class);

    for (Class<?> type : types) {
      String source = Files.readString(sourcePath(type));
      assertTrue(!source.contains("rec" + "ord "), type.getSimpleName() + " 禁止使用 record");
      for (var field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
          continue;
        }
        assertTrue(
            Modifier.isPrivate(field.getModifiers()),
            type.getSimpleName() + "." + field.getName() + " 必须为 private");
        String expression =
            "^\\s*/\\*\\*(?:(?!\\*/).)*[\\u4e00-\\u9fff](?:(?!\\*/).)*\\*/\\R"
                + "(?:\\s*@[A-Za-z0-9_.]+(?:\\([^)]*\\))?)*\\s*private\\s+[^;]+\\b"
                + Pattern.quote(field.getName())
                + "\\s*;";
        assertTrue(
            Pattern.compile(expression, Pattern.MULTILINE | Pattern.DOTALL).matcher(source).find(),
            type.getSimpleName() + "." + field.getName() + " 字段注释、注解或声明未严格分行");
        String suffix =
            Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        assertTrue(
            hasMethod(type, "get" + suffix),
            type.getSimpleName() + "." + field.getName() + " 缺少标准 getter");
        if (!type.isEnum() && type != BusinessException.class) {
          assertTrue(
              hasMethod(type, "set" + suffix, field.getType()),
              type.getSimpleName() + "." + field.getName() + " 缺少标准 setter");
        }
      }
      if (!type.isEnum() && type != BusinessException.class) {
        assertTrue(
            Modifier.isPublic(type.getConstructor().getModifiers()),
            type.getSimpleName() + " 缺少公开无参构造");
      }
    }
  }

  private boolean hasMethod(Class<?> type, String name, Class<?>... parameterTypes) {
    try {
      type.getMethod(name, parameterTypes);
      return true;
    } catch (NoSuchMethodException exception) {
      return false;
    }
  }

  private Path sourcePath(Class<?> type) {
    String module = type.getName().startsWith("com.oa.entity") ? "admin-entity" : "admin-common";
    Path backend = Files.isDirectory(Path.of("backend")) ? Path.of("backend") : Path.of("..");
    return backend
        .resolve(module)
        .resolve("src/main/java")
        .resolve(type.getName().replace('.', '/') + ".java");
  }
}
