package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.model.system.dto.SystemApiQueryDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.entity.system.SystemApiEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 系统接口目录数据访问接口。 */
@Mapper
public interface SystemApiMapper extends BaseMapper<SystemApiEntity> {
  /** 按筛选条件分页查询系统接口。 */
  default IPage<SystemApiEntity> selectSystemApiPage(SystemApiQueryDTO query) {
    String keyword =
        query.getKeyword() == null || query.getKeyword().isBlank()
            ? null
            : query.getKeyword().strip();
    return selectPage(
        new Page<>(query.getPage(), query.getSize()),
        Wrappers.<SystemApiEntity>lambdaQuery()
            .and(
                keyword != null,
                wrapper ->
                    wrapper
                        .like(SystemApiEntity::getName, keyword)
                        .or()
                        .like(SystemApiEntity::getPath, keyword))
            .eq(
                query.getStatus() != null,
                SystemApiEntity::getStatus,
                query.getStatus() == null ? null : query.getStatus().getCode())
            .orderByDesc(SystemApiEntity::getId));
  }

  /** 当前状态符合预期时修改状态，避免覆盖并发人工操作。 */
  default int updateStatusIfCurrent(
      long id, int expectedStatus, int newStatus, LocalDateTime updatedAt) {
    return update(
        null,
        Wrappers.<SystemApiEntity>lambdaUpdate()
            .eq(SystemApiEntity::getId, id)
            .eq(SystemApiEntity::getStatus, expectedStatus)
            .set(SystemApiEntity::getStatus, newStatus)
            .set(SystemApiEntity::getUpdatedAt, updatedAt));
  }

  /** 从指定接口ID中查询已启用的接口ID。 */
  default List<Long> selectEnabledIds(Collection<Long> apiIds) {
    if (apiIds.isEmpty()) {
      return List.of();
    }
    return selectList(
            Wrappers.<SystemApiEntity>lambdaQuery()
                .select(SystemApiEntity::getId)
                .in(SystemApiEntity::getId, apiIds)
                .eq(SystemApiEntity::getStatus, SystemStatus.ENABLED.getCode()))
        .stream()
        .map(SystemApiEntity::getId)
        .toList();
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
