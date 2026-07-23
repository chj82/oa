package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.RoleResourceEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 角色资源关联数据访问接口。 */
@Mapper
public interface RoleResourceMapper extends BaseMapper<RoleResourceEntity> {
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
