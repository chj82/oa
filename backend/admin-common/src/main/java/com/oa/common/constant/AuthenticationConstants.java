package com.oa.common.constant;

/** 登录认证稳定常量。 */
public final class AuthenticationConstants {
  /** 登录令牌 Cookie 名称。 */
  public static final String TOKEN_COOKIE_NAME = "ADMIN_TOKEN";

  /** 当前登录员工请求属性名称。 */
  public static final String CURRENT_EMPLOYEE_ATTRIBUTE =
      AuthenticationConstants.class.getName() + ".employee";

  private AuthenticationConstants() {}
}
