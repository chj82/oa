package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.model.system.dto.EmployeeQueryDTO;
import com.oa.entity.system.EmployeeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 员工数据访问接口。 */
@Mapper
public interface EmployeeMapper extends BaseMapper<EmployeeEntity> {
  /** 锁定指定员工，供无外键关联写入和删除使用。 */
  EmployeeEntity selectByIdForUpdate(@Param("id") long id);

  /** 按登录用户名查询员工。 */
  default EmployeeEntity selectByUsername(String username) {
    return selectOne(
        Wrappers.<EmployeeEntity>lambdaQuery().eq(EmployeeEntity::getUsername, username));
  }

  /** 按员工筛选条件分页查询。 */
  default IPage<EmployeeEntity> selectEmployeePage(EmployeeQueryDTO query) {
    return selectPage(
        new Page<>(query.getPage(), query.getSize()),
        Wrappers.<EmployeeEntity>lambdaQuery()
            .and(
                query.getKeyword() != null && !query.getKeyword().isBlank(),
                wrapper ->
                    wrapper
                        .like(EmployeeEntity::getUsername, query.getKeyword().strip())
                        .or()
                        .like(EmployeeEntity::getName, query.getKeyword().strip()))
            .eq(
                query.getDepartmentId() != null,
                EmployeeEntity::getDepartmentId,
                query.getDepartmentId())
            .eq(
                query.getStatus() != null,
                EmployeeEntity::getStatus,
                query.getStatus() == null ? null : query.getStatus().getCode())
            .orderByDesc(EmployeeEntity::getId));
  }

  /** 按手机号查询员工。 */
  default EmployeeEntity selectByPhone(String phone) {
    return selectOne(Wrappers.<EmployeeEntity>lambdaQuery().eq(EmployeeEntity::getPhone, phone));
  }

  /** 按邮箱查询员工。 */
  default EmployeeEntity selectByEmail(String email) {
    return selectOne(Wrappers.<EmployeeEntity>lambdaQuery().eq(EmployeeEntity::getEmail, email));
  }

  /** 按读取时的状态和超级管理员标记条件更新员工。 */
  default int updateBySnapshot(EmployeeEntity employee, int expectedStatus, int expectedSuperuser) {
    return update(
        employee,
        Wrappers.<EmployeeEntity>lambdaUpdate()
            .eq(EmployeeEntity::getId, employee.getId())
            .eq(EmployeeEntity::getStatus, expectedStatus)
            .eq(EmployeeEntity::getSuperuser, expectedSuperuser));
  }

  /** 确认员工当前状态和超级管理员标记仍与读取快照一致。 */
  default boolean matchesSnapshot(long employeeId, int expectedStatus, int expectedSuperuser) {
    return selectCount(
            Wrappers.<EmployeeEntity>lambdaQuery()
                .eq(EmployeeEntity::getId, employeeId)
                .eq(EmployeeEntity::getStatus, expectedStatus)
                .eq(EmployeeEntity::getSuperuser, expectedSuperuser))
        == 1;
  }

  /** 统计指定部门的员工数量。 */
  default long countByDepartmentId(long departmentId) {
    return selectCount(
        Wrappers.<EmployeeEntity>lambdaQuery().eq(EmployeeEntity::getDepartmentId, departmentId));
  }
}
