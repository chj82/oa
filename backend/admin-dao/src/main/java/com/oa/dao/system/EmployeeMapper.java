package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.model.system.dto.EmployeeQueryDTO;
import com.oa.entity.system.EmployeeEntity;
import org.apache.ibatis.annotations.Mapper;

/** 员工数据访问接口。 */
@Mapper
public interface EmployeeMapper extends BaseMapper<EmployeeEntity> {
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
}
