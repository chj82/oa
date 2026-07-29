import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EmployeesPage } from "@/features/system/employees/employees-page";

let superuser = false;
vi.mock("@/features/auth/use-current-employee", () => ({
  useCurrentEmployee: () => ({
    data: { id: 1, username: "viewer", name: "查看者", superuser, resources: [] },
  }),
}));

describe("员工管理页", () => {
  afterEach(() => vi.unstubAllGlobals());

  beforeEach(() => { superuser = false; });

  it("覆盖加载、空数据和无新增权限状态", async () => {
    let resolveRequest: ((response: Response) => void) | undefined;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string) => {
        if (url.includes("/employees/page")) {
          return new Promise<Response>((resolve) => { resolveRequest = resolve; });
        }
        return Promise.resolve(new Response(JSON.stringify({ code: 0, message: "成功", details: [] }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }));
      }),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={client}>
        <EmployeesPage />
      </QueryClientProvider>,
    );
    expect(screen.getByText("正在加载员工数据…")).toBeInTheDocument();

    resolveRequest?.(
      new Response(
        JSON.stringify({
          code: 0,
          message: "成功",
          details: { records: [], total: 0, page: 1, size: 20 },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );

    expect(await screen.findByText("暂无员工数据")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "新增员工" })).not.toBeInTheDocument();
  });

  it("允许清空员工的全部角色", async () => {
    superuser = true;
    const requests: Array<{ url: string; body?: string }> = [];
    vi.stubGlobal("fetch", vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      requests.push({ url, body: init?.body as string | undefined });
      let details: unknown = [];
      if (url.includes("/employees/page")) details = { records: [{ id: 2, username: "zhangsan", name: "张三", phone: null, email: null, departmentId: 1, status: "ENABLED", superuser: false, createdAt: "", updatedAt: "" }], total: 1, page: 1, size: 20 };
      if (url.includes("/roles/page")) details = { records: [{ id: 10, code: "staff", name: "员工", description: null, status: "ENABLED", createdAt: "", updatedAt: "" }], total: 1, page: 1, size: 200 };
      if (url.includes("/role-ids")) details = [10];
      return Promise.resolve(new Response(JSON.stringify({ code: 0, message: "成功", details }), { status: 200, headers: { "Content-Type": "application/json" } }));
    }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const user = userEvent.setup();

    render(<QueryClientProvider client={client}><EmployeesPage /></QueryClientProvider>);
    await user.click(await screen.findByRole("button", { name: "分配角色" }));
    const checkbox = await screen.findByRole("checkbox", { name: "员工" });
    expect(checkbox).toBeChecked();
    await user.click(checkbox);
    await user.click(screen.getByRole("button", { name: "保存角色" }));

    await vi.waitFor(() => expect(requests.some((request) => request.url.includes("/employees/roles?id=2") && request.body === JSON.stringify({ ids: [] }))).toBe(true));
  });
});
