package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.entity.system.RoleEntity;
import org.apache.ibatis.annotations.Mapper;

/** 角色数据访问接口。 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {}
