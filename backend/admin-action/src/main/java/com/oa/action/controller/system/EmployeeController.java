package com.oa.action.controller.system;

import com.oa.common.constant.AuthenticationConstants;
import com.oa.common.model.system.dto.EmployeePasswordResetDTO;
import com.oa.common.model.system.dto.EmployeeQueryDTO;
import com.oa.common.model.system.dto.EmployeeSaveDTO;
import com.oa.common.model.system.dto.EmployeeStatusDTO;
import com.oa.common.model.system.dto.RelationIdsDTO;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.common.model.system.vo.EmployeeVO;
import com.oa.common.response.ApiResult;
import com.oa.common.response.PageResult;
import com.oa.service.system.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 员工管理接口。 */
@Tag(name = "员工管理", description = "员工信息、状态、密码和角色管理")
@RestController
@RequestMapping("/api/system/employees")
public class EmployeeController {
  private final EmployeeService employeeService;

  public EmployeeController(EmployeeService employeeService) {
    this.employeeService = employeeService;
  }

  @Operation(summary = "分页查询员工")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @GetMapping("/page")
  public ApiResult<PageResult<EmployeeVO>> page(@Valid EmployeeQueryDTO query) {
    return ApiResult.success(employeeService.page(query));
  }

  @Operation(summary = "查询员工详情")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "400", description = "员工不存在")
  @GetMapping("/detail")
  public ApiResult<EmployeeVO> detail(@RequestParam long id) {
    return ApiResult.success(employeeService.detail(id));
  }

  @Operation(summary = "新增员工")
  @ApiResponse(responseCode = "200", description = "新增成功")
  @ApiResponse(responseCode = "400", description = "员工数据不可用")
  @PostMapping("/create")
  public ApiResult<EmployeeVO> create(@Valid @RequestBody EmployeeSaveDTO request) {
    return ApiResult.success(employeeService.create(request));
  }

  @Operation(summary = "修改员工")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "400", description = "员工数据不可用")
  @PostMapping("/update")
  public ApiResult<EmployeeVO> update(
      @Valid @RequestBody EmployeeSaveDTO request, HttpServletRequest servletRequest) {
    return ApiResult.success(employeeService.update(request, currentEmployee(servletRequest)));
  }

  @Operation(summary = "修改员工状态")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "400", description = "员工状态不可修改")
  @PostMapping("/status")
  public ApiResult<Void> status(
      @Valid @RequestBody EmployeeStatusDTO request, HttpServletRequest servletRequest) {
    employeeService.changeStatus(
        request.getId(), request.getStatus(), currentEmployee(servletRequest));
    return ApiResult.success(null);
  }

  @Operation(summary = "删除员工")
  @ApiResponse(responseCode = "200", description = "删除成功")
  @ApiResponse(responseCode = "400", description = "员工不可删除")
  @PostMapping("/delete")
  public ApiResult<Void> delete(@RequestParam long id) {
    employeeService.delete(id);
    return ApiResult.success(null);
  }

  @Operation(summary = "重置员工密码")
  @ApiResponse(responseCode = "200", description = "重置成功")
  @ApiResponse(responseCode = "400", description = "员工不存在")
  @PostMapping("/reset-password")
  public ApiResult<Void> resetPassword(@Valid @RequestBody EmployeePasswordResetDTO request) {
    employeeService.resetPassword(request.getId(), request.getPassword());
    return ApiResult.success(null);
  }

  @Operation(summary = "保存员工角色")
  @ApiResponse(responseCode = "200", description = "保存成功")
  @ApiResponse(responseCode = "400", description = "员工或角色不可用")
  @PostMapping("/roles")
  public ApiResult<Void> roles(
      @RequestParam long id,
      @Valid @RequestBody RelationIdsDTO request,
      HttpServletRequest servletRequest) {
    employeeService.saveRoles(id, request.getIds(), currentEmployee(servletRequest));
    return ApiResult.success(null);
  }

  private CurrentEmployeeVO currentEmployee(HttpServletRequest request) {
    return (CurrentEmployeeVO)
        request.getAttribute(AuthenticationConstants.CURRENT_EMPLOYEE_ATTRIBUTE);
  }
}
