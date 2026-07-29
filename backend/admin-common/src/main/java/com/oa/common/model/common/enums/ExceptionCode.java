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
  SYSTEM_API_NOT_FOUND(3004, "系统接口不存在"),
  SYSTEM_API_CONCURRENT_MODIFICATION(3005, "系统接口已被并发修改，请刷新后重试"),
  SYSTEM_API_DEFINITION_INVALID(3006, "系统接口定义无效"),
  EMPLOYEE_NOT_FOUND(3101, "员工不存在"),
  EMPLOYEE_DEPARTMENT_UNAVAILABLE(3102, "员工所属部门不存在或已禁用"),
  EMPLOYEE_USERNAME_DUPLICATED(3103, "员工用户名已存在"),
  EMPLOYEE_PHONE_DUPLICATED(3104, "员工手机号已存在"),
  EMPLOYEE_EMAIL_DUPLICATED(3105, "员工邮箱已存在"),
  EMPLOYEE_PASSWORD_REQUIRED(3106, "新增员工必须设置密码"),
  EMPLOYEE_SELF_OPERATION_FORBIDDEN(3107, "普通员工不能修改自己的角色、状态或超级管理员标记"),
  EMPLOYEE_SUPERUSER_PROTECTED(3108, "超级管理员员工不能删除或禁用"),
  EMPLOYEE_ROLE_UNAVAILABLE(3109, "待分配角色不存在或已禁用"),
  EMPLOYEE_SUPERUSER_DEMOTION_FORBIDDEN(3110, "超级管理员员工不能降级为普通员工"),
  EMPLOYEE_SELF_DELETE_FORBIDDEN(3111, "员工不能删除自己"),
  EMPLOYEE_PASSWORD_TOO_LONG(3112, "密码超过BCrypt支持的72字节限制"),
  EMPLOYEE_CONCURRENT_MODIFICATION(3113, "员工数据已被并发修改，请刷新后重试"),
  DEPARTMENT_NOT_FOUND(3201, "部门不存在"),
  DEPARTMENT_PARENT_UNAVAILABLE(3202, "父部门不存在或未启用"),
  DEPARTMENT_NAME_DUPLICATED(3203, "同一父部门下部门名称已存在"),
  DEPARTMENT_CYCLE(3204, "部门层级存在环路"),
  DEPARTMENT_DEPTH_EXCEEDED(3205, "部门层级不能超过64层"),
  DEPARTMENT_HAS_CHILDREN(3206, "部门存在子部门，不能删除"),
  DEPARTMENT_HAS_EMPLOYEES(3207, "部门存在员工，不能删除"),
  DEPARTMENT_ENABLED_DESCENDANT_EXISTS(3208, "部门存在启用的子孙部门，不能禁用"),
  DEPARTMENT_CONCURRENT_MODIFICATION(3209, "部门数据已被并发修改，请重试"),
  ROLE_NOT_FOUND(3301, "角色不存在"),
  ROLE_CODE_DUPLICATED(3302, "角色编码已存在"),
  ROLE_NAME_DUPLICATED(3303, "角色名称已存在"),
  ROLE_HAS_EMPLOYEES(3304, "角色已关联员工，不能删除"),
  ROLE_RESOURCE_UNAVAILABLE(3305, "待授权资源不存在或已禁用"),
  ROLE_CONCURRENT_MODIFICATION(3306, "角色数据已被并发修改，请刷新后重试"),
  RESOURCE_NOT_FOUND(3401, "系统资源不存在"),
  RESOURCE_PARENT_UNAVAILABLE(3402, "父资源不存在、已禁用或类型不允许"),
  RESOURCE_CODE_DUPLICATED(3403, "资源编码已存在"),
  RESOURCE_PATH_DUPLICATED(3404, "资源路径已存在"),
  RESOURCE_TYPE_INVALID(3405, "资源类型与字段或层级规则不匹配"),
  RESOURCE_CYCLE(3406, "资源层级存在环路"),
  RESOURCE_DEPTH_EXCEEDED(3407, "资源层级不能超过64层"),
  RESOURCE_HAS_CHILDREN(3408, "资源存在子资源，不能删除"),
  RESOURCE_HAS_ROLES(3409, "资源已关联角色，不能删除"),
  RESOURCE_HAS_APIS(3410, "资源已关联接口，不能删除"),
  RESOURCE_API_UNAVAILABLE(3411, "待关联接口不存在或已禁用"),
  RESOURCE_API_TYPE_INVALID(3412, "目录资源不能关联接口"),
  RESOURCE_TREE_INVALID(3413, "资源树存在孤儿节点或环路"),
  RESOURCE_UNAVAILABLE(3414, "系统资源不存在或未启用"),
  RESOURCE_DIRECTORY_HAS_APIS(3415, "已关联接口的资源不能改为目录"),
  RESOURCE_CONCURRENT_MODIFICATION(3416, "资源已被并发修改，请刷新后重试"),
  INITIALIZATION_USERNAME_REQUIRED(3501, "初始管理员用户名不能为空"),
  INITIALIZATION_PASSWORD_REQUIRED(3502, "初始管理员密码不能为空"),
  INITIALIZATION_API_MISSING(3503, "初始化所需系统接口不存在"),
  INITIALIZATION_RESOURCE_CONFLICT(3504, "初始化资源结构冲突"),
  INITIALIZATION_ADMIN_INVALID(3505, "同名员工不是启用的超级管理员"),
  INITIALIZATION_USERNAME_INVALID(3506, "初始管理员用户名长度必须为1到64个字符"),
  INITIALIZATION_PASSWORD_INVALID(3507, "初始管理员密码至少8个字符且不能超过72字节"),
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
