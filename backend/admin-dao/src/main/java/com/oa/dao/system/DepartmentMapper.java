package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.entity.system.DepartmentEntity;
import org.apache.ibatis.annotations.Mapper;

/** 部门数据访问接口。 */
@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentEntity> {}
