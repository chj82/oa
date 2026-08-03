package com.oa.dao.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.entity.system.SystemVersionEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 系统版本数据访问接口。 */
@Mapper
public interface SystemVersionMapper extends BaseMapper<SystemVersionEntity> {
  /** 按编码查询系统版本。 */
  long selectVersion(@Param("versionCode") String versionCode);

  /** 按编码原子递增系统版本。 */
  int incrementVersion(
      @Param("versionCode") String versionCode, @Param("updatedAt") LocalDateTime updatedAt);

  /** 按编码原子递增并返回最新系统版本。 */
  default long incrementAndSelectVersion(String versionCode) {
    if (incrementVersion(versionCode, LocalDateTime.now()) != 1) {
      throw new IllegalStateException("系统版本不存在，编码=" + versionCode);
    }
    return selectVersion(versionCode);
  }
}
