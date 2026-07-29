package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.EmployeeRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 员工角色关联数据访问接口。 */
@Mapper
public interface EmployeeRoleMapper extends BaseMapper<EmployeeRoleEntity> {
  /** 统计指定角色关联的员工数量。 */
  default long countByRoleId(long roleId) {
    return selectCount(
        Wrappers.<EmployeeRoleEntity>lambdaQuery().eq(EmployeeRoleEntity::getRoleId, roleId));
  }

  /** 按员工ID查询关联的角色ID。 */
  default List<Long> selectRoleIdsByEmployeeId(long employeeId) {
    return selectList(
            Wrappers.<EmployeeRoleEntity>lambdaQuery()
                .select(EmployeeRoleEntity::getRoleId)
                .eq(EmployeeRoleEntity::getEmployeeId, employeeId)
                .orderByAsc(EmployeeRoleEntity::getRoleId))
        .stream()
        .map(EmployeeRoleEntity::getRoleId)
        .distinct()
        .toList();
  }

  /** 删除员工的全部角色关联。 */
  default void deleteByEmployeeId(long employeeId) {
    delete(
        Wrappers.<EmployeeRoleEntity>lambdaQuery()
            .eq(EmployeeRoleEntity::getEmployeeId, employeeId));
  }

  /** 单条SQL批量新增员工角色关联。 */
  int insertBatch(@Param("relations") List<EmployeeRoleEntity> relations);
}
