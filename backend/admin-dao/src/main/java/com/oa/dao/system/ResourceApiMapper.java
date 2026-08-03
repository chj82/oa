package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.ResourceApiEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 资源接口关联数据访问接口。 */
@Mapper
public interface ResourceApiMapper extends BaseMapper<ResourceApiEntity> {
  /** 按资源ID和接口ID查询全部资源接口关联。 */
  default List<ResourceApiEntity> selectAllRelations() {
    return selectList(
        Wrappers.<ResourceApiEntity>lambdaQuery()
            .orderByAsc(ResourceApiEntity::getResourceId)
            .orderByAsc(ResourceApiEntity::getApiId));
  }

  /** 查询指定资源关联的接口ID。 */
  default List<Long> selectApiIdsByResourceId(long resourceId) {
    return selectList(
            Wrappers.<ResourceApiEntity>lambdaQuery()
                .select(ResourceApiEntity::getApiId)
                .eq(ResourceApiEntity::getResourceId, resourceId)
                .orderByAsc(ResourceApiEntity::getApiId))
        .stream()
        .map(ResourceApiEntity::getApiId)
        .toList();
  }

  /** 查询指定资源的接口关联数量。 */
  default long countByResourceId(long resourceId) {
    return selectCount(
        Wrappers.<ResourceApiEntity>lambdaQuery().eq(ResourceApiEntity::getResourceId, resourceId));
  }

  /** 删除指定资源的接口关联。 */
  default void deleteByResourceId(long resourceId) {
    delete(
        Wrappers.<ResourceApiEntity>lambdaQuery().eq(ResourceApiEntity::getResourceId, resourceId));
  }

  /** 单条SQL批量新增资源接口关联。 */
  int insertBatch(@Param("relations") List<ResourceApiEntity> relations);

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
