package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.entity.system.ResourceApiEntity;
import org.apache.ibatis.annotations.Mapper;

/** 资源接口关联数据访问接口。 */
@Mapper
public interface ResourceApiMapper extends BaseMapper<ResourceApiEntity> {}
