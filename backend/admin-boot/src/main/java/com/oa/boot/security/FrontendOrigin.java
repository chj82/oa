package com.oa.boot.security;

import java.net.URI;
import java.net.URISyntaxException;

/** 前端来源规范化工具。 */
public final class FrontendOrigin {
  private FrontendOrigin() {}

  /** 校验并移除单个尾斜杠。 */
  public static String normalize(String value) {
    if (value == null || value.isBlank() || value.contains(",") || "*".equals(value)) {
      throw new IllegalArgumentException("frontend-origin必须是单一明确来源");
    }
    String normalized = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    try {
      URI uri = new URI(normalized);
      if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || (uri.getPath() != null && !uri.getPath().isEmpty())
          || uri.getQuery() != null
          || uri.getFragment() != null) {
        throw new IllegalArgumentException("frontend-origin格式无效");
      }
      return normalized;
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("frontend-origin格式无效", exception);
    }
  }

  /** 从 Referer 中提取规范来源。 */
  public static String fromReferer(String referer) {
    if (referer == null || referer.isBlank()) {
      return null;
    }
    try {
      URI uri = new URI(referer);
      if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) {
        return null;
      }
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null)
          .toString();
    } catch (URISyntaxException exception) {
      return null;
    }
  }
}
