package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.RoleEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 角色数据访问接口。 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
  /** 从指定角色ID中查询已启用的角色ID。 */
  default List<Long> selectEnabledIds(Collection<Long> roleIds) {
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return selectList(
            Wrappers.<RoleEntity>lambdaQuery()
                .select(RoleEntity::getId)
                .in(RoleEntity::getId, roleIds)
                .eq(RoleEntity::getStatus, 1))
        .stream()
        .map(RoleEntity::getId)
        .toList();
  }
}
