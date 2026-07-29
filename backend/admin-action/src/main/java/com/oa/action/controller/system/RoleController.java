package com.oa.action.controller.system;

import com.oa.common.model.system.dto.RoleCreateDTO;
import com.oa.common.model.system.dto.RoleQueryDTO;
import com.oa.common.model.system.dto.RoleResourceSaveDTO;
import com.oa.common.model.system.dto.RoleStatusDTO;
import com.oa.common.model.system.dto.RoleUpdateDTO;
import com.oa.common.model.system.vo.RoleVO;
import com.oa.common.response.ApiResult;
import com.oa.common.response.PageResult;
import com.oa.service.system.RoleService;
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

/** 角色管理接口。 */
@Tag(name = "角色管理", description = "角色信息、状态和资源授权管理")
@Validated
@RestController
@RequestMapping("/api/system/roles")
public class RoleController {
  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @Operation(summary = "分页查询角色")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "查询参数校验失败")
  @GetMapping("/page")
  public ApiResult<PageResult<RoleVO>> page(@Valid RoleQueryDTO query) {
    return ApiResult.success(roleService.page(query));
  }

  @Operation(summary = "查询角色详情")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  @GetMapping("/detail")
  public ApiResult<RoleVO> detail(@RequestParam("id") @Positive long id) {
    return ApiResult.success(roleService.detail(id));
  }

  @Operation(summary = "查询角色资源ID")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  @GetMapping("/resource-ids")
  public ApiResult<List<Long>> resourceIds(@RequestParam("roleId") @Positive long roleId) {
    return ApiResult.success(roleService.resourceIds(roleId));
  }

  @Operation(summary = "新增角色")
  @ApiResponse(responseCode = "200", description = "新增成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/create")
  public ApiResult<RoleVO> create(@Valid @RequestBody RoleCreateDTO request) {
    return ApiResult.success(roleService.create(request));
  }

  @Operation(summary = "修改角色")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/update")
  public ApiResult<RoleVO> update(@Valid @RequestBody RoleUpdateDTO request) {
    return ApiResult.success(roleService.update(request));
  }

  @Operation(summary = "修改角色状态")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/status")
  public ApiResult<Void> status(@Valid @RequestBody RoleStatusDTO request) {
    roleService.changeStatus(request.getId(), request.getStatus());
    return ApiResult.success(null);
  }

  @Operation(summary = "删除角色")
  @ApiResponse(responseCode = "200", description = "删除成功")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  @PostMapping("/delete")
  public ApiResult<Void> delete(@RequestParam("id") @Positive long id) {
    roleService.delete(id);
    return ApiResult.success(null);
  }

  @Operation(summary = "保存角色资源")
  @ApiResponse(responseCode = "200", description = "保存成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/resources")
  public ApiResult<Void> resources(
      @RequestParam("id") @Positive long id, @Valid @RequestBody RoleResourceSaveDTO request) {
    roleService.saveResources(id, request.getIds());
    return ApiResult.success(null);
  }
}
