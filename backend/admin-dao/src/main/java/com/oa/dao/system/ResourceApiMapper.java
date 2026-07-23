package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.ResourceApiEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 资源接口关联数据访问接口。 */
@Mapper
public interface ResourceApiMapper extends BaseMapper<ResourceApiEntity> {
  /** 查询指定资源集合关联的接口ID。 */
  default List<Long> selectApiIdsByResourceIds(Collection<Long> resourceIds) {
    if (resourceIds.isEmpty()) {
      return List.of();
    }
    return selectList(
            Wrappers.<ResourceApiEntity>lambdaQuery()
                .select(ResourceApiEntity::getApiId)
                .in(ResourceApiEntity::getResourceId, resourceIds))
        .stream()
        .map(ResourceApiEntity::getApiId)
        .distinct()
        .toList();
  }
}
