package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.model.system.dto.RoleQueryDTO;
import com.oa.entity.system.RoleEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 角色数据访问接口。 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
  /** 按ID锁定角色，供无外键关联写事务使用。 */
  RoleEntity selectByIdForUpdate(@Param("id") long id);

  /** 按ID升序锁定角色集合，供无外键关联写事务使用。 */
  List<RoleEntity> selectByIdsForUpdate(@Param("ids") Collection<Long> ids);

  /** 按筛选条件分页查询角色。 */
  default IPage<RoleEntity> selectRolePage(RoleQueryDTO query) {
    return selectPage(
        new Page<>(query.getPage(), query.getSize()),
        Wrappers.<RoleEntity>lambdaQuery()
            .and(
                query.getKeyword() != null && !query.getKeyword().isBlank(),
                wrapper ->
                    wrapper
                        .like(RoleEntity::getCode, query.getKeyword().strip())
                        .or()
                        .like(RoleEntity::getName, query.getKeyword().strip()))
            .eq(
                query.getStatus() != null,
                RoleEntity::getStatus,
                query.getStatus() == null ? null : query.getStatus().getCode())
            .orderByDesc(RoleEntity::getId));
  }

  /** 按角色编码查询角色。 */
  default RoleEntity selectByCode(String code) {
    return selectOne(Wrappers.<RoleEntity>lambdaQuery().eq(RoleEntity::getCode, code));
  }

  /** 按角色名称查询角色。 */
  default RoleEntity selectByName(String name) {
    return selectOne(Wrappers.<RoleEntity>lambdaQuery().eq(RoleEntity::getName, name));
  }

  /** 按读取时的状态条件更新角色。 */
  default int updateBySnapshot(RoleEntity role, int expectedStatus) {
    return update(
        role,
        Wrappers.<RoleEntity>lambdaUpdate()
            .eq(RoleEntity::getId, role.getId())
            .eq(RoleEntity::getStatus, expectedStatus));
  }

  /** 确认角色当前状态仍与读取快照一致。 */
  default boolean matchesSnapshot(long roleId, int expectedStatus) {
    return selectCount(
            Wrappers.<RoleEntity>lambdaQuery()
                .eq(RoleEntity::getId, roleId)
                .eq(RoleEntity::getStatus, expectedStatus))
        == 1;
  }

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
