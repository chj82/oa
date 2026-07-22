package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.entity.system.RoleResourceEntity;
import org.apache.ibatis.annotations.Mapper;

/** 角色资源关联数据访问接口。 */
@Mapper
public interface RoleResourceMapper extends BaseMapper<RoleResourceEntity> {}
