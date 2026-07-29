package com.oa.action.controller.system;

import com.oa.common.model.system.dto.DepartmentCreateDTO;
import com.oa.common.model.system.dto.DepartmentStatusDTO;
import com.oa.common.model.system.dto.DepartmentUpdateDTO;
import com.oa.common.model.system.vo.DepartmentVO;
import com.oa.common.response.ApiResult;
import com.oa.service.system.DepartmentService;
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

/** 部门管理接口。 */
@Tag(name = "部门管理", description = "部门树、信息和状态管理")
@RestController
@Validated
@RequestMapping("/api/system/departments")
public class DepartmentController {
  private final DepartmentService departmentService;

  public DepartmentController(DepartmentService departmentService) {
    this.departmentService = departmentService;
  }

  @Operation(summary = "查询部门树")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @GetMapping("/tree")
  public ApiResult<List<DepartmentVO>> tree() {
    return ApiResult.success(departmentService.tree());
  }

  @Operation(summary = "查询部门详情")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "400", description = "部门不存在")
  @GetMapping("/detail")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  public ApiResult<DepartmentVO> detail(@RequestParam("id") @Positive long id) {
    return ApiResult.success(departmentService.detail(id));
  }

  @Operation(summary = "新增部门")
  @ApiResponse(responseCode = "200", description = "新增成功")
  @ApiResponse(responseCode = "400", description = "部门数据不可用")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/create")
  public ApiResult<DepartmentVO> create(@Valid @RequestBody DepartmentCreateDTO request) {
    return ApiResult.success(departmentService.create(request));
  }

  @Operation(summary = "修改部门")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "400", description = "部门数据不可用")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/update")
  public ApiResult<DepartmentVO> update(@Valid @RequestBody DepartmentUpdateDTO request) {
    return ApiResult.success(departmentService.update(request));
  }

  @Operation(summary = "修改部门状态")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "400", description = "部门状态不可修改")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/status")
  public ApiResult<Void> status(@Valid @RequestBody DepartmentStatusDTO request) {
    departmentService.changeStatus(request.getId(), request.getStatus());
    return ApiResult.success(null);
  }

  @Operation(summary = "删除部门")
  @ApiResponse(responseCode = "200", description = "删除成功")
  @ApiResponse(responseCode = "400", description = "部门不可删除")
  @PostMapping("/delete")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  public ApiResult<Void> delete(@RequestParam("id") @Positive long id) {
    departmentService.delete(id);
    return ApiResult.success(null);
  }
}
