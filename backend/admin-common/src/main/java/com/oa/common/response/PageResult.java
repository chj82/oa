package com.oa.common.response;

import java.util.List;

/** 分页查询结果。 */
public class PageResult<T> {
  /** 当前页记录。 */
  private List<T> records;

  /** 总记录数。 */
  private long total;

  /** 当前页码。 */
  private long page;

  /** 每页记录数。 */
  private long size;

  public PageResult() {}

  public List<T> getRecords() {
    return records;
  }

  public void setRecords(List<T> records) {
    this.records = records;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

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
