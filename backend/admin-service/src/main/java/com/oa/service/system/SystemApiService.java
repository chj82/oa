package com.oa.service.system;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.SystemApiDefinitionDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.dao.system.SystemApiMapper;
import com.oa.entity.system.SystemApiEntity;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统接口目录同步服务。 */
@Service
public class SystemApiService {
  private final SystemApiMapper systemApiMapper;
  private final PermissionService permissionService;

  public SystemApiService(SystemApiMapper systemApiMapper, PermissionService permissionService) {
    this.systemApiMapper = systemApiMapper;
    this.permissionService = permissionService;
  }

  /** 按代码中的路由定义同步系统接口目录。 */
  @Transactional
  public void sync(List<SystemApiDefinitionDTO> definitions) {
    Map<String, SystemApiDefinitionDTO> definitionsByPath = uniqueDefinitions(definitions);
    Map<String, SystemApiEntity> existingByPath = new LinkedHashMap<>();
    for (SystemApiEntity existing : systemApiMapper.selectAll()) {
      existingByPath.put(existing.getPath(), existing);
    }

    LocalDateTime now = LocalDateTime.now();
    boolean changed = false;
    for (SystemApiDefinitionDTO definition : definitionsByPath.values()) {
      SystemApiEntity existing = existingByPath.remove(definition.getPath());
      if (existing == null) {
        systemApiMapper.insert(newApi(definition, now));
        changed = true;
      } else if (metadataChanged(existing, definition)) {
        existing.setName(definition.getName());
        existing.setDescription(definition.getDescription());
        existing.setUpdatedAt(now);
        systemApiMapper.updateById(existing);
        changed = true;
      }
    }

    for (SystemApiEntity removed : existingByPath.values()) {
      if (removed.getStatus() == SystemStatus.ENABLED.getCode()) {
        removed.setStatus(SystemStatus.DISABLED.getCode());
        removed.setUpdatedAt(now);
        systemApiMapper.updateById(removed);
        changed = true;
      }
    }
    if (changed) {
      permissionService.invalidateAll();
    } else {
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
}
