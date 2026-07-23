package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.EmployeeRoleEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 员工角色关联数据访问接口。 */
@Mapper
public interface EmployeeRoleMapper extends BaseMapper<EmployeeRoleEntity> {
  /** 按员工ID查询关联的角色ID。 */
  default List<Long> selectRoleIdsByEmployeeId(long employeeId) {
    return selectList(
            Wrappers.<EmployeeRoleEntity>lambdaQuery()
                .select(EmployeeRoleEntity::getRoleId)
                .eq(EmployeeRoleEntity::getEmployeeId, employeeId))
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

  /** 批量新增员工角色关联。 */
  default void insertRelations(long employeeId, List<Long> roleIds, LocalDateTime createdAt) {
    for (Long roleId : roleIds) {
      EmployeeRoleEntity relation = new EmployeeRoleEntity();
      relation.setEmployeeId(employeeId);
      relation.setRoleId(roleId);
      relation.setCreatedAt(createdAt);
      insert(relation);
    }
  }
}
