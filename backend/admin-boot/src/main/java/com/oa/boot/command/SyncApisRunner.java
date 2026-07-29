package com.oa.boot.command;

import com.oa.boot.security.SecurityPathRules;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.SystemApiDefinitionDTO;
import com.oa.service.system.SystemApiService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 仅在显式命令模式下扫描并同步 Spring MVC 接口目录。 */
@Component
public class SyncApisRunner implements ApplicationRunner {
  private static final String SYNC_APIS_COMMAND = "sync-apis";

  private final RequestMappingHandlerMapping handlerMapping;
  private final SystemApiService systemApiService;
  private final String command;

  public SyncApisRunner(
      RequestMappingHandlerMapping handlerMapping,
      SystemApiService systemApiService,
      @Value("${app.command:}") String command) {
    this.handlerMapping = handlerMapping;
    this.systemApiService = systemApiService;
    this.command = command;
  }

  /** 执行显式接口同步命令，普通启动不读取路由表。 */
  @Override
  public void run(ApplicationArguments arguments) {
    if (!SYNC_APIS_COMMAND.equals(command)) {
      return;
    }

    List<SystemApiDefinitionDTO> definitions = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        handlerMapping.getHandlerMethods().entrySet()) {
      for (String path : entry.getKey().getPatternValues()) {
        if (!SecurityPathRules.isApiCatalogExcluded(path)) {
          if (entry.getKey().getMethodsCondition().getMethods().size() != 1) {
            throw new BusinessException(
                ExceptionCode.SYSTEM_API_METHOD_INVALID, "接口HTTP方法配置无效：" + path);
          }
          definitions.add(definition(path, entry.getValue()));
        }
      }
    }
    systemApiService.sync(definitions);
  }

  private SystemApiDefinitionDTO definition(String path, HandlerMethod handlerMethod) {
    Operation operation = handlerMethod.getMethodAnnotation(Operation.class);
    if (operation == null) {
      throw new BusinessException(
          ExceptionCode.SYSTEM_API_OPERATION_MISSING, "接口缺少OpenAPI说明：" + path);
    }

    SystemApiDefinitionDTO definition = new SystemApiDefinitionDTO();
    definition.setName(operation.summary() == null ? null : operation.summary().strip());
    definition.setPath(path);
    definition.setDescription(
        operation.description() == null || operation.description().isBlank()
            ? null
            : operation.description().strip());
    validateDefinition(definition, handlerMethod);
    return definition;
  }

  private void validateDefinition(SystemApiDefinitionDTO definition, HandlerMethod handlerMethod) {
    boolean invalidName = definition.getName() == null || definition.getName().isBlank();
    boolean invalidPath = definition.getPath() == null || definition.getPath().isBlank();
    boolean invalidDescription =
        definition.getDescription() != null && definition.getDescription().length() > 500;
    if (invalidName
        || (!invalidName && definition.getName().length() > 100)
        || invalidPath
        || (!invalidPath && definition.getPath().length() > 255)
        || invalidDescription) {
      throw new BusinessException(
          ExceptionCode.SYSTEM_API_DEFINITION_INVALID,
          "系统接口定义无效：" + handlerName(handlerMethod) + "，路径：" + definition.getPath());
    }
  }

  private String handlerName(HandlerMethod handlerMethod) {
    if (handlerMethod.getMethod() == null) {
      return "未知Handler";
    }
    return handlerMethod.getMethod().getDeclaringClass().getSimpleName()
        + "#"
        + handlerMethod.getMethod().getName();
  }
}
