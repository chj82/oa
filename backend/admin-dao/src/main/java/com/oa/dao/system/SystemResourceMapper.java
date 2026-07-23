package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.SystemResourceEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 系统资源数据访问接口。 */
@Mapper
public interface SystemResourceMapper extends BaseMapper<SystemResourceEntity> {
  /** 按资源ID集合批量查询实际存在的资源。 */
  default List<SystemResourceEntity> selectExistingByIds(Collection<Long> resourceIds) {
    if (resourceIds.isEmpty()) {
      return List.of();
    }
    return selectList(
        Wrappers.<SystemResourceEntity>lambdaQuery().in(SystemResourceEntity::getId, resourceIds));
  }
}
