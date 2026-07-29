package com.oa.action.controller.system;

import com.oa.common.context.CurrentEmployeeContext;
import com.oa.common.model.system.dto.EmployeeCreateDTO;
import com.oa.common.model.system.dto.EmployeePasswordResetDTO;
import com.oa.common.model.system.dto.EmployeeQueryDTO;
import com.oa.common.model.system.dto.EmployeeStatusDTO;
import com.oa.common.model.system.dto.EmployeeUpdateDTO;
import com.oa.common.model.system.dto.RelationIdsDTO;
import com.oa.common.model.system.vo.EmployeeVO;
import com.oa.common.response.ApiResult;
import com.oa.common.response.PageResult;
import com.oa.service.system.EmployeeService;
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

/** 员工管理接口。 */
@Tag(name = "员工管理", description = "员工信息、状态、密码和角色管理")
@Validated
@RestController
@RequestMapping("/api/system/employees")
public class EmployeeController {
  private final EmployeeService employeeService;

  public EmployeeController(EmployeeService employeeService) {
    this.employeeService = employeeService;
  }

  @Operation(summary = "分页查询员工")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "查询参数校验失败")
  @GetMapping("/page")
  public ApiResult<PageResult<EmployeeVO>> page(@Valid EmployeeQueryDTO query) {
    return ApiResult.success(employeeService.page(query));
  }

  @Operation(summary = "查询员工详情")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "400", description = "员工不存在")
  @GetMapping("/detail")
  @ApiResponse(responseCode = "422", description = "员工ID必须为正数")
  public ApiResult<EmployeeVO> detail(@Positive @RequestParam("id") long id) {
    return ApiResult.success(employeeService.detail(id));
  }

  @Operation(summary = "查询员工角色")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "400", description = "员工不存在")
  @ApiResponse(responseCode = "422", description = "员工ID必须为正数")
  @GetMapping("/role-ids")
  public ApiResult<List<Long>> roleIds(@Positive @RequestParam("id") long id) {
    return ApiResult.success(employeeService.roleIds(id));
  }

  @Operation(summary = "新增员工")
  @ApiResponse(responseCode = "200", description = "新增成功")
  @ApiResponse(responseCode = "400", description = "员工数据不可用")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/create")
  public ApiResult<EmployeeVO> create(@Valid @RequestBody EmployeeCreateDTO request) {
    return ApiResult.success(employeeService.create(request));
  }

  @Operation(summary = "修改员工")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "400", description = "员工数据不可用")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/update")
  public ApiResult<EmployeeVO> update(@Valid @RequestBody EmployeeUpdateDTO request) {
    return ApiResult.success(employeeService.update(request, CurrentEmployeeContext.get()));
  }

  @Operation(summary = "修改员工状态")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "400", description = "员工状态不可修改")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/status")
  public ApiResult<Void> status(@Valid @RequestBody EmployeeStatusDTO request) {
    employeeService.changeStatus(
        request.getId(), request.getStatus(), CurrentEmployeeContext.get());
    return ApiResult.success(null);
  }

  @Operation(summary = "删除员工")
  @ApiResponse(responseCode = "200", description = "删除成功")
  @ApiResponse(responseCode = "400", description = "员工不可删除")
  @ApiResponse(responseCode = "422", description = "员工ID必须为正数")
  @PostMapping("/delete")
  public ApiResult<Void> delete(@Positive @RequestParam("id") long id) {
    employeeService.delete(id, CurrentEmployeeContext.get().getId());
    return ApiResult.success(null);
  }

  @Operation(summary = "重置员工密码")
  @ApiResponse(responseCode = "200", description = "重置成功")
  @ApiResponse(responseCode = "400", description = "员工不存在")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/reset-password")
  public ApiResult<Void> resetPassword(@Valid @RequestBody EmployeePasswordResetDTO request) {
    employeeService.resetPassword(request.getId(), request.getPassword());
    return ApiResult.success(null);
  }

  @Operation(summary = "保存员工角色")
  @ApiResponse(responseCode = "200", description = "保存成功")
  @ApiResponse(responseCode = "400", description = "员工或角色不可用")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/roles")
  public ApiResult<Void> roles(
      @Positive @RequestParam("id") long id, @Valid @RequestBody RelationIdsDTO request) {
    employeeService.saveRoles(id, request.getIds(), CurrentEmployeeContext.get());
    return ApiResult.success(null);
  }
}
