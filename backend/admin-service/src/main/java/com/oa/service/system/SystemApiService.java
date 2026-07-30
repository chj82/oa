package com.oa.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.SystemApiQueryDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.SystemApiVO;
import com.oa.common.response.PageResult;
import com.oa.dao.system.SystemApiMapper;
import com.oa.entity.system.SystemApiEntity;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统接口目录管理服务。 */
@Service
public class SystemApiService {
  private final SystemApiMapper systemApiMapper;
  private final PermissionService permissionService;

  public SystemApiService(SystemApiMapper systemApiMapper, PermissionService permissionService) {
    this.systemApiMapper = systemApiMapper;
    this.permissionService = permissionService;
  }

  /** 分页查询系统接口目录。 */
  public PageResult<SystemApiVO> page(SystemApiQueryDTO query) {
    IPage<SystemApiEntity> entityPage = systemApiMapper.selectSystemApiPage(query);
    PageResult<SystemApiVO> result = new PageResult<>();
    result.setRecords(entityPage.getRecords().stream().map(this::toVO).toList());
    result.setTotal(entityPage.getTotal());
    result.setPage(entityPage.getCurrent());
    result.setSize(entityPage.getSize());
    return result;
  }

  /** 查询系统接口详情。 */
  public SystemApiVO detail(long apiId) {
    return toVO(requiredApi(apiId));
  }

  /** 修改系统接口启停状态。 */
  @Transactional
  public void changeStatus(long apiId, SystemStatus status) {
    SystemApiEntity api = requiredApi(apiId);
    if (api.getStatus() == status.getCode()) {
      return;
    }
    permissionService.invalidateAll();
    int affected =
        systemApiMapper.updateStatusIfCurrent(
            apiId, api.getStatus(), status.getCode(), LocalDateTime.now());
    if (affected == 0) {
      throw new BusinessException(ExceptionCode.SYSTEM_API_CONCURRENT_MODIFICATION);
    }
  }

  private SystemApiEntity requiredApi(long apiId) {
    SystemApiEntity api = systemApiMapper.selectById(apiId);
    if (api == null) {
      throw new BusinessException(ExceptionCode.SYSTEM_API_NOT_FOUND);
    }
    return api;
  }

  private SystemApiVO toVO(SystemApiEntity api) {
    SystemApiVO result = new SystemApiVO();
    result.setId(api.getId());
    result.setName(api.getName());
    result.setPath(api.getPath());
    result.setDescription(api.getDescription());
    result.setStatus(
        api.getStatus() == SystemStatus.ENABLED.getCode()
            ? SystemStatus.ENABLED
            : SystemStatus.DISABLED);
    result.setCreatedAt(api.getCreatedAt());
    result.setUpdatedAt(api.getUpdatedAt());
    return result;
  }
}
