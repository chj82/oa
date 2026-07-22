package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
}
