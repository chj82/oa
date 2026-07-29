package com.oa.action.controller.system;

import com.oa.common.model.system.dto.SystemApiQueryDTO;
import com.oa.common.model.system.dto.SystemApiStatusDTO;
import com.oa.common.model.system.vo.SystemApiVO;
import com.oa.common.response.ApiResult;
import com.oa.common.response.PageResult;
import com.oa.service.system.SystemApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统接口目录管理接口。 */
@Tag(name = "系统接口目录", description = "查询和启停代码同步的系统接口")
@Validated
@RestController
@RequestMapping("/api/system/apis")
public class SystemApiController {
  private final SystemApiService systemApiService;

  public SystemApiController(SystemApiService systemApiService) {
    this.systemApiService = systemApiService;
  }

  @Operation(summary = "分页查询系统接口")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "查询参数校验失败")
  @GetMapping("/page")
  public ApiResult<PageResult<SystemApiVO>> page(@Valid SystemApiQueryDTO query) {
    return ApiResult.success(systemApiService.page(query));
  }

  @Operation(summary = "查询系统接口详情")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "422", description = "请求参数校验失败")
  @GetMapping("/detail")
  public ApiResult<SystemApiVO> detail(@RequestParam("id") @Positive long id) {
    return ApiResult.success(systemApiService.detail(id));
  }

  @Operation(summary = "修改系统接口状态")
  @ApiResponse(responseCode = "200", description = "修改成功")
  @ApiResponse(responseCode = "422", description = "请求字段校验失败")
  @PostMapping("/status")
  public ApiResult<Void> status(@Valid @RequestBody SystemApiStatusDTO request) {
    systemApiService.changeStatus(request.getId(), request.getStatus());
    return ApiResult.success(null);
  }
}
