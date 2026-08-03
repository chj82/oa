import { ApiClientError, apiRequest } from "@/lib/api-client";

describe("apiRequest", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("默认携带 Cookie 并解析成功响应", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ success: true, code: 0, message: "成功", details: { id: 1 } }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiRequest<{ id: number }>("/api/auth/current")).resolves.toEqual({ id: 1 });
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/auth/current"),
      expect.objectContaining({ credentials: "include" }),
    );
  });

  it("浏览器收到 401 时跳转登录页", async () => {
    const replace = vi.fn();
    vi.stubGlobal("window", {
      dispatchEvent: vi.fn(),
      history: { replaceState: vi.fn() },
      location: { pathname: "/dashboard", replace },
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 401 })));

    await expect(apiRequest("/api/auth/current")).rejects.toBeInstanceOf(ApiClientError);
    expect(replace).toHaveBeenCalledWith("/login");
  });

  it("非 2xx 响应抛出包含服务端信息的错误", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ success: false, code: 4220, message: "参数错误", details: null }),
          {
            status: 422,
            headers: { "Content-Type": "application/json" },
          },
        ),
      ),
    );

    await expect(apiRequest("/api/test")).rejects.toMatchObject({
      code: 4220,
      message: "参数错误",
      status: 422,
    });
  });

  it("HTTP 成功但 success 为 false 时抛出业务错误", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ success: false, code: 0, message: "登录失败", details: null }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          },
        ),
      ),
    );

    await expect(apiRequest("/api/auth/login")).rejects.toMatchObject({
      code: 0,
      message: "登录失败",
    });
  });
});
