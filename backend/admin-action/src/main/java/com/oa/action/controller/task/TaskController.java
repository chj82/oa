package com.oa.action.controller.task;

import com.oa.common.response.ApiResult;
import com.oa.service.task.TaskManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 查询和手动执行后台任务。 */
@Tag(name = "任务管理", description = "查询和手动执行后台任务")
@Validated
@RestController
@RequestMapping("/api/admin/task")
public class TaskController {
  private final TaskManager taskManager;

  public TaskController(TaskManager taskManager) {
    this.taskManager = taskManager;
  }

  @Operation(summary = "查询任务列表")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @GetMapping("/list.htm")
  public ApiResult<List<String>> list() {
    return ApiResult.success(taskManager.list());
  }

  @Operation(summary = "手动执行任务")
  @ApiResponse(responseCode = "200", description = "任务已提交")
  @ApiResponse(responseCode = "400", description = "任务不存在")
  @ApiResponse(responseCode = "422", description = "任务名称不能为空")
  @PostMapping("/run.htm")
  public ApiResult<Boolean> run(
      @RequestParam("name") @NotBlank String name,
      @RequestParam(value = "data", required = false) String data) {
    taskManager.run(name, data);
    return ApiResult.success(true);
  }
}
