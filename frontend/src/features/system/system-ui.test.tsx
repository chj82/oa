import { render, screen } from "@testing-library/react";
import { DataState } from "@/features/system/system-ui";
import { ApiClientError } from "@/lib/api-client";

describe("系统页面状态", () => {
  it("接口返回 403 时显示无权限且不提供无效重试", () => {
    render(
      <DataState
        loading={false}
        error={new ApiClientError("禁止访问", 403, 403)}
        empty={false}
        emptyText="暂无数据"
        onRetry={vi.fn()}
      >
        <div>数据内容</div>
      </DataState>,
    );

    expect(screen.getByText("暂无权限访问")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重新加载" })).not.toBeInTheDocument();
  });
});
