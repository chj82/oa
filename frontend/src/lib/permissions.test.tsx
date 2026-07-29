import { render, screen } from "@testing-library/react";
import { Permission } from "@/components/permission";
import { buildNavigation } from "@/lib/permissions";
import type { Resource } from "@/types/api";

const resources: Resource[] = [
  {
    id: 1,
    parentId: null,
    type: "DIRECTORY",
    name: "系统管理",
    code: "system",
    path: null,
    icon: "Settings",
    sortOrder: 20,
    visible: true,
    status: "ENABLED",
    children: [
      {
        id: 2,
        parentId: 1,
        type: "MENU",
        name: "员工管理",
        code: "system:employee",
        path: "/system/employees",
        icon: "Users",
        sortOrder: 10,
        visible: true,
        status: "ENABLED",
        children: [],
      },
      {
        id: 3,
        parentId: 1,
        type: "ACTION",
        name: "新增员工",
        code: "system:employee:create",
        path: null,
        icon: null,
        sortOrder: 30,
        visible: true,
        status: "ENABLED",
        children: [],
      },
    ],
  },
  {
    id: 4,
    parentId: null,
    type: "MENU",
    name: "隐藏菜单",
    code: "hidden",
    path: "/hidden",
    icon: null,
    sortOrder: 1,
    visible: false,
    status: "ENABLED",
    children: [],
  },
];

describe("权限资源", () => {
  it("仅构建启用且可见的目录和菜单，并稳定排序", () => {
    const navigation = buildNavigation(resources);

    expect(navigation).toHaveLength(1);
    expect(navigation[0]).toMatchObject({ name: "系统管理", clickable: false });
    expect(navigation[0].children).toEqual([
      expect.objectContaining({ name: "员工管理", path: "/system/employees", clickable: true }),
    ]);
  });

  it("资源为空时返回空导航", () => {
    expect(buildNavigation(undefined)).toEqual([]);
  });

  it("有 ACTION 编码时显示按钮", () => {
    render(
      <Permission resources={resources} action="system:employee:create">
        <button>新增</button>
      </Permission>,
    );
    expect(screen.getByRole("button", { name: "新增" })).toBeInTheDocument();
  });

  it("没有 ACTION 编码时隐藏按钮", () => {
    render(
      <Permission resources={resources} action="system:employee:delete">
        <button>删除</button>
      </Permission>,
    );
    expect(screen.queryByRole("button", { name: "删除" })).not.toBeInTheDocument();
  });

  it("超级管理员无需 ACTION 编码也显示按钮", () => {
    render(
      <Permission resources={[]} action="system:employee:delete" superuser>
        <button>删除</button>
      </Permission>,
    );
    expect(screen.getByRole("button", { name: "删除" })).toBeInTheDocument();
  });
});
