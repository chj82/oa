package com.oa.common.model.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oa.common.exception.BusinessException;
import com.oa.common.response.ApiResult;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 统一异常码测试。 */
class ExceptionCodeTest {
  /** 所有异常码数字唯一，非成功码使用正数，名称使用中文。 */
  @Test
  void 异常码唯一且名称为中文() {
    Set<Integer> codes =
        Arrays.stream(ExceptionCode.values())
            .map(ExceptionCode::getCode)
            .collect(Collectors.toSet());

    assertEquals(ExceptionCode.values().length, codes.size());
    assertEquals(0, ExceptionCode.SUCCESS.getCode());
    for (ExceptionCode exceptionCode : ExceptionCode.values()) {
      if (exceptionCode != ExceptionCode.SUCCESS) {
        assertTrue(exceptionCode.getCode() > 0);
      }
      assertTrue(exceptionCode.getName().matches(".*[\\u4e00-\\u9fff].*"));
    }
  }

  /** 业务异常默认展示枚举名称，也允许为路径等上下文补充自定义消息。 */
  @Test
  void 业务异常持有统一异常枚举() {
    BusinessException defaultException = new BusinessException(ExceptionCode.FORBIDDEN);
    BusinessException contextualException =
        new BusinessException(ExceptionCode.RESOURCE_PATH_DUPLICATED, "资源路径重复：/system/orders");

    assertEquals(ExceptionCode.FORBIDDEN, defaultException.getExceptionCode());
    assertEquals(ExceptionCode.FORBIDDEN.getName(), defaultException.getMessage());
    assertEquals(ExceptionCode.RESOURCE_PATH_DUPLICATED, contextualException.getExceptionCode());
    assertEquals("资源路径重复：/system/orders", contextualException.getMessage());
  }

  /** 统一响应使用数字异常码和枚举中文名称。 */
  @Test
  void 统一响应使用异常枚举() {
    ApiResult<String> success = ApiResult.success("ok");
    ApiResult<Void> forbidden = ApiResult.error(ExceptionCode.FORBIDDEN);

    assertEquals(0, success.getCode());
    assertEquals(ExceptionCode.SUCCESS.getName(), success.getMessage());
    assertEquals(ExceptionCode.FORBIDDEN.getCode(), forbidden.getCode());
    assertEquals(ExceptionCode.FORBIDDEN.getName(), forbidden.getMessage());
  }
}
