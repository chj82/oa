package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.RoleResourceEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 角色资源关联数据访问接口。 */
@Mapper
public interface RoleResourceMapper extends BaseMapper<RoleResourceEntity> {
  /** 查询指定资源的角色关联数量。 */
  default long countByResourceId(long resourceId) {
    return selectCount(
        Wrappers.<RoleResourceEntity>lambdaQuery()
            .eq(RoleResourceEntity::getResourceId, resourceId));
  }

  /** 删除角色的全部资源关联。 */
  default void deleteByRoleId(long roleId) {
    delete(Wrappers.<RoleResourceEntity>lambdaQuery().eq(RoleResourceEntity::getRoleId, roleId));
  }

  /** 单条SQL批量新增角色资源关联。 */
  int insertBatch(@Param("relations") List<RoleResourceEntity> relations);

  /** 按角色ID查询关联的资源ID。 */
  default List<Long> selectResourceIdsByRoleId(long roleId) {
    return selectList(
            Wrappers.<RoleResourceEntity>lambdaQuery()
                .select(RoleResourceEntity::getResourceId)
                .eq(RoleResourceEntity::getRoleId, roleId)
                .orderByAsc(RoleResourceEntity::getResourceId))
        .stream()
        .map(RoleResourceEntity::getResourceId)
        .toList();
  }

  /** 按角色ID集合查询关联的资源ID。 */
  default List<Long> selectResourceIdsByRoleIds(Collection<Long> roleIds) {
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return selectList(
            Wrappers.<RoleResourceEntity>lambdaQuery()
                .select(RoleResourceEntity::getResourceId)
                .in(RoleResourceEntity::getRoleId, roleIds))
        .stream()
        .map(RoleResourceEntity::getResourceId)
        .distinct()
        .toList();
  }
}
