package com.oa.common.model.common.enums;

/** 统一异常码。 */
public enum ExceptionCode {
  SUCCESS(0, "成功"),
  INVALID_CREDENTIALS(1001, "用户名或密码错误"),
  UNAUTHORIZED(1002, "未登录或登录态已失效"),
  FORBIDDEN(1003, "无权访问该接口"),
  INVALID_REQUEST_ORIGIN(1004, "请求来源不可信"),
  VALIDATION_FAILED(2001, "请求字段校验失败"),
  SYSTEM_API_PATH_DUPLICATED(3001, "接口路径重复"),
  SYSTEM_API_OPERATION_MISSING(3002, "接口缺少OpenAPI说明"),
  SYSTEM_API_METHOD_INVALID(3003, "接口HTTP方法配置无效"),
  EMPLOYEE_NOT_FOUND(3101, "员工不存在"),
  EMPLOYEE_DEPARTMENT_UNAVAILABLE(3102, "员工所属部门不存在或已禁用"),
  EMPLOYEE_USERNAME_DUPLICATED(3103, "员工用户名已存在"),
  EMPLOYEE_PHONE_DUPLICATED(3104, "员工手机号已存在"),
  EMPLOYEE_EMAIL_DUPLICATED(3105, "员工邮箱已存在"),
  EMPLOYEE_PASSWORD_REQUIRED(3106, "新增员工必须设置密码"),
  EMPLOYEE_SELF_OPERATION_FORBIDDEN(3107, "普通员工不能修改自己的角色、状态或超级管理员标记"),
  EMPLOYEE_SUPERUSER_PROTECTED(3108, "超级管理员员工不能删除或禁用"),
  EMPLOYEE_ROLE_UNAVAILABLE(3109, "待分配角色不存在或已禁用"),
  INFRASTRUCTURE_UNAVAILABLE(9001, "基础设施暂不可用"),
  AUTH_INFRASTRUCTURE_UNAVAILABLE(9002, "认证服务暂不可用"),
  INTERNAL_ERROR(9999, "系统异常");

  /** 数字异常码。 */
  private final int code;

  /** 中文异常名称。 */
  private final String name;

  ExceptionCode(int code, String name) {
    this.code = code;
    this.name = name;
  }

  public int getCode() {
    return code;
  }

  public String getName() {
    return name;
  }
}
