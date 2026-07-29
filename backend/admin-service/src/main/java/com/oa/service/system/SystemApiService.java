package com.oa.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.SystemApiDefinitionDTO;
import com.oa.common.model.system.dto.SystemApiQueryDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.SystemApiVO;
import com.oa.common.response.PageResult;
import com.oa.dao.system.SystemApiMapper;
import com.oa.entity.system.SystemApiEntity;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统接口目录同步服务。 */
@Service
public class SystemApiService {
  private static final String PATH_UNIQUE_INDEX = "udx_system_api_path";
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

  /** 按代码中的路由定义同步系统接口目录。 */
  @Transactional
  public void sync(List<SystemApiDefinitionDTO> definitions) {
    Map<String, SystemApiDefinitionDTO> definitionsByPath = uniqueDefinitions(definitions);
    Map<String, SystemApiEntity> existingByPath = new LinkedHashMap<>();
    for (SystemApiEntity existing : systemApiMapper.selectAll()) {
      existingByPath.put(existing.getPath(), existing);
    }

    List<SystemApiDefinitionDTO> additions =
        definitionsByPath.values().stream()
            .filter(definition -> !existingByPath.containsKey(definition.getPath()))
            .toList();
    List<SystemApiEntity> removals =
        existingByPath.values().stream()
            .filter(existing -> !definitionsByPath.containsKey(existing.getPath()))
            .filter(existing -> existing.getStatus() == SystemStatus.ENABLED.getCode())
            .toList();
    boolean permissionChanged = !additions.isEmpty() || !removals.isEmpty();
    boolean metadataChanged =
        definitionsByPath.values().stream()
            .anyMatch(
                definition -> {
                  SystemApiEntity existing = existingByPath.get(definition.getPath());
                  return existing != null && metadataChanged(existing, definition);
                });
    if (permissionChanged) {
      permissionService.invalidateAll();
    }

    LocalDateTime now = LocalDateTime.now();
    for (SystemApiDefinitionDTO definition : definitionsByPath.values()) {
      SystemApiEntity existing = existingByPath.remove(definition.getPath());
      if (existing == null) {
        try {
          systemApiMapper.insert(newApi(definition, now));
        } catch (DuplicateKeyException exception) {
          throw translateDuplicateKey(exception);
        }
      } else if (metadataChanged(existing, definition)) {
        systemApiMapper.updateMetadata(
            existing.getId(), definition.getName(), definition.getDescription(), now);
      }
    }

    for (SystemApiEntity removed : existingByPath.values()) {
      if (removed.getStatus() == SystemStatus.ENABLED.getCode()) {
        systemApiMapper.updateStatusIfCurrent(
            removed.getId(), SystemStatus.ENABLED.getCode(), SystemStatus.DISABLED.getCode(), now);
      }
    }
    if (!permissionChanged && !metadataChanged) {
      permissionService.retryPendingInvalidation();
    }
  }

  private Map<String, SystemApiDefinitionDTO> uniqueDefinitions(
      List<SystemApiDefinitionDTO> definitions) {
    Map<String, SystemApiDefinitionDTO> definitionsByPath = new LinkedHashMap<>();
    for (SystemApiDefinitionDTO definition : definitions) {
      if (definitionsByPath.putIfAbsent(definition.getPath(), definition) != null) {
        throw new BusinessException(
            ExceptionCode.SYSTEM_API_PATH_DUPLICATED, "接口路径重复：" + definition.getPath());
      }
    }
    return definitionsByPath;
  }

  private SystemApiEntity newApi(SystemApiDefinitionDTO definition, LocalDateTime now) {
    SystemApiEntity api = new SystemApiEntity();
    api.setName(definition.getName());
    api.setPath(definition.getPath());
    api.setDescription(definition.getDescription());
    api.setStatus(SystemStatus.ENABLED.getCode());
    api.setCreatedAt(now);
    api.setUpdatedAt(now);
    return api;
  }

  private boolean metadataChanged(SystemApiEntity existing, SystemApiDefinitionDTO definition) {
    return !Objects.equals(existing.getName(), definition.getName())
        || !Objects.equals(existing.getDescription(), definition.getDescription());
  }

  private SystemApiEntity requiredApi(long apiId) {
    SystemApiEntity api = systemApiMapper.selectById(apiId);
    if (api == null) {
      throw new BusinessException(ExceptionCode.SYSTEM_API_NOT_FOUND);
    }
    return api;
  }

  private RuntimeException translateDuplicateKey(DuplicateKeyException exception) {
    String message = exception.getMessage();
    if (message != null && message.contains(PATH_UNIQUE_INDEX)) {
      return new BusinessException(ExceptionCode.SYSTEM_API_PATH_DUPLICATED);
    }
    return exception;
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
