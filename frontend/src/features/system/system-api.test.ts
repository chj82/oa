import {
  createEmployee,
  getAllApis,
  getAllRoles,
  getDepartmentTree,
  getRoleResourceIds,
  saveResourceApis,
  searchApis,
} from "@/features/system/system-api";

describe("系统管理接口", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("按后端契约发送员工新增请求", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ success: true, code: 0, message: "成功", details: { id: 8 } }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await createEmployee({
      username: "zhangsan",
      name: "张三",
      password: "password1",
      phone: "",
      email: "",
      departmentId: 2,
      status: "ENABLED",
      superuser: false,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/system/employees/create"),
      expect.objectContaining({ method: "POST", credentials: "include" }),
    );
  });

  it("使用准确查询参数读取树、授权和接口分页", async () => {
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({ success: true, code: 0, message: "成功", details: [] }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          },
        ),
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await getDepartmentTree();
    await getRoleResourceIds(3);
    await searchApis({ page: 2, size: 20, keyword: "员工", status: "ENABLED" });
    await saveResourceApis(5, [11, 12]);

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      expect.stringContaining("/api/system/departments/tree"),
      expect.stringContaining("/api/system/roles/resource-ids?roleId=3"),
      expect.stringContaining("/api/system/apis/page?page=2&size=20&keyword=%E5%91%98%E5%B7%A5&status=ENABLED"),
      expect.stringContaining("/api/system/resources/apis"),
    ]);
    expect(JSON.parse(String(fetchMock.mock.calls[3][1]?.body))).toEqual({
      resourceId: 5,
      apiIds: [11, 12],
    });
  });

  it("关联候选超过单页上限时读取全部分页", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      const page = new URL(url).searchParams.get("page");
      return Promise.resolve(
        new Response(
          JSON.stringify({
            success: true,
            code: 0,
            message: "成功",
            details: {
              records: [{ id: Number(page) }],
              total: 201,
              page: Number(page),
              size: 200,
            },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      );
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(getAllRoles()).resolves.toHaveLength(2);
    await expect(getAllApis()).resolves.toHaveLength(2);
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      expect.stringContaining("/roles/page?page=1&size=200"),
      expect.stringContaining("/roles/page?page=2&size=200"),
      expect.stringContaining("/apis/page?page=1&size=200"),
      expect.stringContaining("/apis/page?page=2&size=200"),
    ]);
  });
});
