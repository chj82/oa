"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { Permission } from "@/components/permission";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useCurrentEmployee } from "@/features/auth/use-current-employee";
import {
  changeDepartmentStatus,
  createDepartment,
  deleteDepartment,
  getDepartmentTree,
  updateDepartment,
} from "@/features/system/system-api";
import {
  ConfirmButton,
  DataState,
  fieldClass,
  labelClass,
  Modal,
  PageHeader,
} from "@/features/system/system-ui";
import { TreeRows } from "@/features/system/tree-view";
import type { Department } from "@/types/api";

function flattenDepartments(nodes: Department[], depth = 0): Array<{ id: number; name: string }> {
  return nodes.flatMap((node) => [
    { id: node.id, name: `${"　".repeat(depth)}${node.name}` },
    ...flattenDepartments(node.children ?? [], depth + 1),
  ]);
}

function descendantIds(node: Department): Set<number> {
  const ids = new Set<number>();
  const visit = (children: Department[]) => children.forEach((child) => {
    ids.add(child.id);
    visit(child.children ?? []);
  });
  visit(node.children ?? []);
  return ids;
}

export function DepartmentsPage() {
  const client = useQueryClient();
  const current = useCurrentEmployee().data;
  const query = useQuery({ queryKey: ["departments", "tree"], queryFn: getDepartmentTree });
  const [editing, setEditing] = useState<Department | null>();
  const [name, setName] = useState("");
  const [parentId, setParentId] = useState(0);
  const [sortOrder, setSortOrder] = useState(0);
  const [status, setStatus] = useState<"ENABLED" | "DISABLED">("ENABLED");
  const refresh = () => client.invalidateQueries({ queryKey: ["departments"] });
  const save = useMutation({
    mutationFn: () => editing
      ? updateDepartment(editing.id, { name, parentId, sortOrder, status })
      : createDepartment({ name, parentId, sortOrder, status }),
    onSuccess: () => {
      setEditing(undefined);
      refresh();
    },
  });
  const action = useMutation({ mutationFn: (run: () => Promise<void>) => run(), onSuccess: refresh });
  const open = (node: Department | null) => {
    setEditing(node);
    setName(node?.name ?? "");
    setParentId(node?.parentId ?? 0);
    setSortOrder(node?.sortOrder ?? 0);
    setStatus(node?.status ?? "ENABLED");
  };
  const permission = (actionCode: string, child: React.ReactNode) => (
    <Permission action={actionCode} resources={current?.resources} superuser={current?.superuser}>{child}</Permission>
  );
  const excludedParents = editing ? descendantIds(editing) : new Set<number>();

  return (
    <>
      <PageHeader title="部门管理" actions={permission("system:department:create", <Button onClick={() => open(null)}><Plus className="size-4" />新增部门</Button>)} />
      <div className="overflow-hidden rounded-md border border-neutral-200 bg-white">
        <DataState loading={query.isPending} error={query.error} empty={!query.data?.length} emptyText="暂无部门数据" onRetry={() => query.refetch()}>
          <TreeRows nodes={query.data ?? []} renderActions={(node) => <>
            {permission("system:department:update", <Button title="编辑部门" className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200" onClick={() => open(node)}><Pencil className="size-4" /></Button>)}
            {permission("system:department:status", <Button className="h-8 bg-white px-2 text-neutral-700 ring-1 ring-neutral-200" disabled={action.isPending} onClick={() => action.mutate(() => changeDepartmentStatus(node.id, node.status === "ENABLED" ? "DISABLED" : "ENABLED"))}>{node.status === "ENABLED" ? "停用" : "启用"}</Button>)}
            {permission("system:department:delete", <ConfirmButton message={`确认删除部门“${node.name}”？`} className="size-8 bg-white p-0 text-red-600 ring-1 ring-neutral-200" disabled={action.isPending} onConfirm={() => action.mutate(() => deleteDepartment(node.id))}><Trash2 className="size-4" /></ConfirmButton>)}
          </>} />
        </DataState>
      </div>
      {action.error && <p className="mt-3 text-sm text-red-600">{action.error.message}</p>}
      <Modal title={editing ? "编辑部门" : "新增部门"} open={editing !== undefined} onClose={() => setEditing(undefined)}>
        <div className="grid gap-4">
          <label className={labelClass}>部门名称<Input value={name} onChange={(event) => setName(event.target.value)} /></label>
          <label className={labelClass}>上级部门<select className={fieldClass} value={parentId} onChange={(event) => setParentId(Number(event.target.value))}><option value={0}>根部门</option>{flattenDepartments(query.data ?? []).filter((item) => item.id !== editing?.id && !excludedParents.has(item.id)).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label className={labelClass}>排序<Input type="number" min={0} value={sortOrder} onChange={(event) => setSortOrder(Number(event.target.value))} /></label>
          <label className={labelClass}>状态<select className={fieldClass} value={status} onChange={(event) => setStatus(event.target.value as typeof status)}><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label>
          {save.error && <p className="text-sm text-red-600">{save.error.message}</p>}
          <Button disabled={!name.trim() || save.isPending} onClick={() => save.mutate()}>保存</Button>
        </div>
      </Modal>
    </>
  );
}
