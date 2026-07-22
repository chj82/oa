package com.oa.common.response;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 分页查询参数。 */
public class PageQuery {
  /** 页码，从一开始。 */
  @Min(1)
  private long page;

  /** 每页记录数。 */
  @Min(1)
  @Max(200)
  private long size;

  public PageQuery() {}

  public long getPage() {
    return page;
  }

  public void setPage(long page) {
    this.page = page;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }
}
