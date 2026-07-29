package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.SystemResourceEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 系统资源数据访问接口。 */
@Mapper
public interface SystemResourceMapper extends BaseMapper<SystemResourceEntity> {
  /** 按排序值和ID查询全部资源。 */
  default List<SystemResourceEntity> selectAllOrdered() {
    return selectList(
        Wrappers.<SystemResourceEntity>lambdaQuery()
            .orderByAsc(SystemResourceEntity::getSortOrder)
            .orderByAsc(SystemResourceEntity::getId));
  }

  /** 按资源编码查询。 */
  default SystemResourceEntity selectByCode(String code) {
    return selectOne(
        Wrappers.<SystemResourceEntity>lambdaQuery().eq(SystemResourceEntity::getCode, code));
  }

  /** 按父资源和名称查询资源。 */
  default SystemResourceEntity selectByParentIdAndName(long parentId, String name) {
    return selectOne(
        Wrappers.<SystemResourceEntity>lambdaQuery()
            .eq(SystemResourceEntity::getParentId, parentId)
            .eq(SystemResourceEntity::getName, name));
  }

  /** 按菜单路径查询。 */
  default SystemResourceEntity selectByPath(String path) {
    return selectOne(
        Wrappers.<SystemResourceEntity>lambdaQuery().eq(SystemResourceEntity::getPath, path));
  }

  /** 查询直接子资源数量。 */
  default long countByParentId(long parentId) {
    return selectCount(
        Wrappers.<SystemResourceEntity>lambdaQuery()
            .eq(SystemResourceEntity::getParentId, parentId));
  }

  /** 锁定指定资源，供无外键关联写入使用。 */
  SystemResourceEntity selectByIdForUpdate(@Param("id") long id);

  /** 按ID升序锁定资源集合，供无外键关联写入使用。 */
  List<SystemResourceEntity> selectByIdsForUpdate(@Param("ids") Collection<Long> ids);

  /** 从指定资源ID中查询已启用的资源ID。 */
  default List<Long> selectEnabledIds(Collection<Long> resourceIds) {
    if (resourceIds.isEmpty()) {
      return List.of();
    }
    return selectList(
            Wrappers.<SystemResourceEntity>lambdaQuery()
                .select(SystemResourceEntity::getId)
                .in(SystemResourceEntity::getId, resourceIds)
                .eq(SystemResourceEntity::getStatus, 1))
        .stream()
        .map(SystemResourceEntity::getId)
        .toList();
  }

  /** 按资源ID集合批量查询实际存在的资源。 */
  default List<SystemResourceEntity> selectExistingByIds(Collection<Long> resourceIds) {
    if (resourceIds.isEmpty()) {
      return List.of();
    }
    return selectList(
        Wrappers.<SystemResourceEntity>lambdaQuery().in(SystemResourceEntity::getId, resourceIds));
  }
}
