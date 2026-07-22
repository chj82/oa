package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.entity.system.EmployeeEntity;
import org.apache.ibatis.annotations.Mapper;

/** 员工数据访问接口。 */
@Mapper
public interface EmployeeMapper extends BaseMapper<EmployeeEntity> {}
