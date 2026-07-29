package com.oa.action.controller.system;

import com.oa.common.model.system.dto.ResourceApiSaveDTO;
import com.oa.common.model.system.dto.ResourceCreateDTO;
import com.oa.common.model.system.dto.ResourceStatusDTO;
import com.oa.common.model.system.dto.ResourceUpdateDTO;
import com.oa.common.model.system.vo.ResourceVO;
import com.oa.common.response.ApiResult;
import com.oa.service.system.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统资源管理接口。 */
@Tag(name = "系统资源管理", description = "资源树及资源接口关联管理")
@Validated
@RestController
@RequestMapping("/api/system/resources")
public class ResourceController {
  private final ResourceService resourceService;

  public ResourceController(ResourceService resourceService) {
    this.resourceService = resourceService;
  }

  @Operation(summary = "查询资源树")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  @GetMapping("/tree")
  public ApiResult<List<ResourceVO>> tree() {
    return ApiResult.success(resourceService.tree());
  }

  @Operation(summary = "查询资源详情")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  @GetMapping("/detail")
  public ApiResult<ResourceVO> detail(@RequestParam("id") @Positive long id) {
    return ApiResult.success(resourceService.detail(id));
  }

  @Operation(summary = "新增资源")
  @ApiResponse(responseCode = "200", description = "新增成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/create")
  public ApiResult<ResourceVO> create(@Valid @RequestBody ResourceCreateDTO request) {
    return ApiResult.success(resourceService.create(request));
  }

  @Operation(summary = "修改资源")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/update")
  public ApiResult<ResourceVO> update(@Valid @RequestBody ResourceUpdateDTO request) {
    return ApiResult.success(resourceService.update(request));
  }

  @Operation(summary = "修改资源状态")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/status")
  public ApiResult<Void> status(@Valid @RequestBody ResourceStatusDTO request) {
    resourceService.changeStatus(request.getId(), request.getStatus());
    return ApiResult.success(null);
  }

  @Operation(summary = "删除资源")
  @ApiResponse(responseCode = "200", description = "删除成功")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  @PostMapping("/delete")
  public ApiResult<Void> delete(@RequestParam("id") @Positive long id) {
    resourceService.delete(id);
    return ApiResult.success(null);
  }

  @Operation(summary = "查询资源接口ID")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  @GetMapping("/api-ids")
  public ApiResult<List<Long>> apiIds(@RequestParam("resourceId") @Positive long resourceId) {
    return ApiResult.success(resourceService.apiIds(resourceId));
  }

  @Operation(summary = "保存资源接口关联")
  @ApiResponse(responseCode = "200", description = "保存成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/apis")
  public ApiResult<Void> apis(@Valid @RequestBody ResourceApiSaveDTO request) {
    resourceService.saveApis(request.getResourceId(), request.getApiIds());
    return ApiResult.success(null);
  }
}
