package com.oa.boot.security;

import java.util.Set;

/** 登录认证、资源鉴权和接口同步共用的固定路径规则。 */
public final class SecurityPathRules {
  private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/login", "/error");
  private static final Set<String> AUTHENTICATION_ONLY_PATHS =
      Set.of("/api/auth/current", "/api/auth/logout");

  private SecurityPathRules() {}

  /** 判断路径是否无需登录即可访问。 */
  public static boolean isPublicPath(String path) {
    return PUBLIC_PATHS.contains(path)
        || "/swagger-ui.html".equals(path)
        || "/swagger-ui".equals(path)
        || path.startsWith("/swagger-ui/")
        || "/v3/api-docs".equals(path)
        || path.startsWith("/v3/api-docs/");
  }

  /** 判断路径是否仅需登录、不参与资源接口鉴权。 */
  public static boolean isAuthenticationOnlyPath(String path) {
    return AUTHENTICATION_ONLY_PATHS.contains(path);
  }

  /** 判断路径是否不写入接口目录。 */
  public static boolean isApiCatalogExcluded(String path) {
    return isPublicPath(path) || isAuthenticationOnlyPath(path);
  }

  /** 返回资源接口鉴权拦截器的固定排除路径表达式。 */
  public static String[] authorizationExcludedPatterns() {
    return new String[] {
      "/api/auth/login",
      "/api/auth/current",
      "/api/auth/logout",
      "/error",
      "/swagger-ui.html",
      "/swagger-ui",
      "/swagger-ui/**",
      "/v3/api-docs",
      "/v3/api-docs/**"
    };
  }
}
