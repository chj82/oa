package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oa.entity.system.DepartmentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 部门数据访问接口。 */
@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentEntity> {
  /** 查询全部部门并稳定排序。 */
  default List<DepartmentEntity> selectAllOrdered() {
    return selectList(
        Wrappers.<DepartmentEntity>lambdaQuery()
            .orderByAsc(DepartmentEntity::getSortOrder)
            .orderByAsc(DepartmentEntity::getId));
  }

  /** 按父部门和名称查询部门。 */
  default DepartmentEntity selectByParentIdAndName(long parentId, String name) {
    return selectOne(
        Wrappers.<DepartmentEntity>lambdaQuery()
            .eq(DepartmentEntity::getParentId, parentId)
            .eq(DepartmentEntity::getName, name));
  }

  /** 统计部门的直接子部门数量。 */
  default long countByParentId(long parentId) {
    return selectCount(
        Wrappers.<DepartmentEntity>lambdaQuery().eq(DepartmentEntity::getParentId, parentId));
  }

  /** 按ID查询并锁定部门行。 */
  DepartmentEntity selectByIdForUpdate(@Param("id") long id);

  /** 按ID升序批量查询并锁定部门行。 */
  List<DepartmentEntity> selectByIdsForUpdate(@Param("ids") List<Long> ids);
}
