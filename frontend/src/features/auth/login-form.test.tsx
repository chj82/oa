import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LoginForm } from "@/features/auth/login-form";

describe("LoginForm", () => {
  it("阻止空用户名和短密码提交", async () => {
    const onSuccess = vi.fn();
    render(<LoginForm onSuccess={onSuccess} />);

    await userEvent.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByText("请输入用户名")).toBeInTheDocument();
    expect(screen.getByText("密码至少 6 位")).toBeInTheDocument();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("提交登录并在成功后回调", async () => {
    const onSuccess = vi.fn();
    const request = vi.fn().mockResolvedValue(undefined);
    render(<LoginForm onSuccess={onSuccess} request={request} />);

    await userEvent.type(screen.getByLabelText("用户名"), "admin");
    await userEvent.type(screen.getByLabelText("密码"), "password");
    await userEvent.click(screen.getByRole("button", { name: "登录" }));

    expect(request).toHaveBeenCalledWith({ username: "admin", password: "password" });
    expect(onSuccess).toHaveBeenCalledTimes(1);
  });

  it("提交期间禁用按钮并显示接口错误", async () => {
    const request = vi.fn().mockRejectedValue(new Error("用户名或密码错误"));
    render(<LoginForm onSuccess={vi.fn()} request={request} />);

    await userEvent.type(screen.getByLabelText("用户名"), "admin");
    await userEvent.type(screen.getByLabelText("密码"), "password");
    await userEvent.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByText("用户名或密码错误")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "登录" })).toBeEnabled();
  });
});
