import { calculateCheckedState, toggleTreeSelection } from "@/features/system/tree-selection";
import type { Resource } from "@/types/api";

const tree: Resource[] = [
  {
    id: 1,
    parentId: 0,
    type: "DIRECTORY",
    name: "系统管理",
    code: "system",
    path: null,
    icon: null,
    sortOrder: 0,
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
        icon: null,
        sortOrder: 0,
        visible: true,
        status: "ENABLED",
        children: [
          {
            id: 3,
            parentId: 2,
            type: "ACTION",
            name: "新增",
            code: "system:employee:create",
            path: null,
            icon: null,
            sortOrder: 0,
            visible: true,
            status: "ENABLED",
          },
        ],
      },
    ],
  },
];

describe("树授权选择", () => {
  it("选中父节点时同时选中全部后代", () => {
    expect(toggleTreeSelection(tree, new Set(), 1)).toEqual(new Set([1, 2, 3]));
  });

  it("只选中部分后代时父节点为半选", () => {
    expect(calculateCheckedState(tree, new Set([3]), 1)).toBe("indeterminate");
  });

  it("取消子节点时移除祖先的全选状态", () => {
    expect(toggleTreeSelection(tree, new Set([1, 2, 3]), 3)).toEqual(new Set());
  });
});
