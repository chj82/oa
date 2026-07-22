package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.entity.system.EmployeeRoleEntity;
import org.apache.ibatis.annotations.Mapper;

/** 员工角色关联数据访问接口。 */
@Mapper
public interface EmployeeRoleMapper extends BaseMapper<EmployeeRoleEntity> {}
