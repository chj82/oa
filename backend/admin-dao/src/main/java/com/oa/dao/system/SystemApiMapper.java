package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.entity.system.SystemApiEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 系统接口目录数据访问接口。 */
@Mapper
public interface SystemApiMapper extends BaseMapper<SystemApiEntity> {
  /** 查询全部系统接口目录。 */
  default List<SystemApiEntity> selectAll() {
    return selectList(null);
  }

  /** 按 Spring MVC 路由模板查询系统接口。 */
  default SystemApiEntity selectByPath(String path) {
    return selectOne(Wrappers.<SystemApiEntity>lambdaQuery().eq(SystemApiEntity::getPath, path));
  }

  /** 查询全部启用接口的路由模板。 */
  default List<String> selectEnabledPaths() {
    return selectList(
            Wrappers.<SystemApiEntity>lambdaQuery()
                .select(SystemApiEntity::getPath)
                .eq(SystemApiEntity::getStatus, SystemStatus.ENABLED.getCode()))
        .stream()
        .map(SystemApiEntity::getPath)
        .toList();
  }

  /** 查询指定接口ID集合中启用接口的路由模板。 */
  default List<String> selectEnabledPathsByIds(Collection<Long> apiIds) {
    if (apiIds.isEmpty()) {
      return List.of();
    }
    return selectList(
            Wrappers.<SystemApiEntity>lambdaQuery()
                .select(SystemApiEntity::getPath)
                .in(SystemApiEntity::getId, apiIds)
                .eq(SystemApiEntity::getStatus, SystemStatus.ENABLED.getCode()))
        .stream()
        .map(SystemApiEntity::getPath)
        .toList();
  }
}
