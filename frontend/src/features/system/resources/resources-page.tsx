"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link2, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Permission } from "@/components/permission";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useCurrentEmployee } from "@/features/auth/use-current-employee";
import {
  changeResourceStatus,
  createResource,
  deleteResource,
  getAllApis,
  getResourceApiIds,
  getResourceTree,
  saveResourceApis,
  updateResource,
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
import type { Resource, ResourceType, ResourceWriteRequest } from "@/types/api";

const emptyForm: ResourceWriteRequest = {
  parentId: 0,
  type: "DIRECTORY",
  name: "",
  code: "",
  path: "",
  icon: "",
  sortOrder: 0,
  visible: true,
  status: "ENABLED",
};

function flattenResources(
  nodes: Resource[],
  depth = 0,
): Array<{ id: number; name: string }> {
  return nodes.flatMap((node) => [
    { id: node.id, name: `${"　".repeat(depth)}${node.name}` },
    ...flattenResources(node.children ?? [], depth + 1),
  ]);
}

function descendantIds(node: Resource): Set<number> {
  const ids = new Set<number>();
  const visit = (children: Resource[]) => {
    children.forEach((child) => {
      ids.add(child.id);
      visit(child.children ?? []);
    });
  };
  visit(node.children ?? []);
  return ids;
}

export function ResourcesPage() {
  const client = useQueryClient();
  const current = useCurrentEmployee().data;
  const query = useQuery({ queryKey: ["resources", "tree"], queryFn: getResourceTree });
  const [editing, setEditing] = useState<Resource | null>();
  const [relation, setRelation] = useState<Resource>();
  const [form, setForm] = useState<ResourceWriteRequest>(emptyForm);
  const refresh = () => client.invalidateQueries({ queryKey: ["resources"] });
  const save = useMutation({
    mutationFn: () =>
      editing ? updateResource(editing.id, form) : createResource(form),
    onSuccess: () => {
      setEditing(undefined);
      refresh();
    },
  });
  const action = useMutation({
    mutationFn: (run: () => Promise<void>) => run(),
    onSuccess: refresh,
  });
  const open = (node: Resource | null) => {
    setEditing(node);
    setForm(
      node
        ? {
            parentId: node.parentId ?? 0,
            type: node.type,
            name: node.name,
            code: node.code ?? "",
            path: node.path ?? "",
            icon: node.icon ?? "",
            sortOrder: node.sortOrder,
            visible: node.visible,
            status: node.status,
          }
        : emptyForm,
    );
  };
  const permission = (actionCode: string, child: React.ReactNode) => (
    <Permission action={actionCode} resources={current?.resources} superuser={current?.superuser}>
      {child}
    </Permission>
  );
  const excludedParents = editing ? descendantIds(editing) : new Set<number>();

  return (
    <>
      <PageHeader
        title="资源管理"
        actions={permission(
          "system:resource:create",
          <Button onClick={() => open(null)}>
            <Plus className="size-4" />新增资源
          </Button>,
        )}
      />
      <div className="overflow-hidden rounded-md border border-neutral-200 bg-white">
        <DataState
          loading={query.isPending}
          error={query.error}
          empty={!query.data?.length}
          emptyText="暂无资源数据"
          onRetry={() => query.refetch()}
        >
          <TreeRows
            nodes={query.data ?? []}
            renderActions={(node) => (
              <>
                <span className="mr-2 self-center text-xs text-neutral-500">{node.type}</span>
                {permission(
                  "system:resource:update",
                  <Button title="编辑资源" className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200" onClick={() => open(node)}>
                    <Pencil className="size-4" />
                  </Button>,
                )}
                {permission(
                  "system:resource:apis",
                  <Button title="关联接口" className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200" onClick={() => setRelation(node)}>
                    <Link2 className="size-4" />
                  </Button>,
                )}
                {permission(
                  "system:resource:status",
                  <Button className="h-8 bg-white px-2 text-neutral-700 ring-1 ring-neutral-200" disabled={action.isPending} onClick={() => action.mutate(() => changeResourceStatus(node.id, node.status === "ENABLED" ? "DISABLED" : "ENABLED"))}>
                    {node.status === "ENABLED" ? "停用" : "启用"}
                  </Button>,
                )}
                {permission(
                  "system:resource:delete",
                  <ConfirmButton message={`确认删除资源“${node.name}”？`} className="size-8 bg-white p-0 text-red-600 ring-1 ring-neutral-200" disabled={action.isPending} onConfirm={() => action.mutate(() => deleteResource(node.id))}>
                    <Trash2 className="size-4" />
                  </ConfirmButton>,
                )}
              </>
            )}
          />
        </DataState>
      </div>
      {action.error && <p className="mt-3 text-sm text-red-600">{action.error.message}</p>}
      <Modal title={editing ? "编辑资源" : "新增资源"} open={editing !== undefined} onClose={() => setEditing(undefined)}>
        <div className="grid gap-3">
          <label className={labelClass}>名称<Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
          <div className="grid grid-cols-2 gap-3">
            <label className={labelClass}>类型<select className={fieldClass} value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value as ResourceType })}><option value="DIRECTORY">目录</option><option value="MENU">菜单</option><option value="ACTION">按钮</option></select></label>
            <label className={labelClass}>上级<select className={fieldClass} value={form.parentId} onChange={(event) => setForm({ ...form, parentId: Number(event.target.value) })}><option value={0}>根资源</option>{flattenResources(query.data ?? []).filter((item) => item.id !== editing?.id && !excludedParents.has(item.id)).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          </div>
          <label className={labelClass}>资源编码<Input value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} /></label>
          <div className="grid grid-cols-2 gap-3">
            <label className={labelClass}>路由<Input value={form.path} onChange={(event) => setForm({ ...form, path: event.target.value })} /></label>
            <label className={labelClass}>图标<Input value={form.icon} onChange={(event) => setForm({ ...form, icon: event.target.value })} /></label>
          </div>
          <label className={labelClass}>排序<Input type="number" min={0} value={form.sortOrder} onChange={(event) => setForm({ ...form, sortOrder: Number(event.target.value) })} /></label>
          <div className="flex gap-6 text-sm">
            <label><input className="mr-2" type="checkbox" checked={form.visible} onChange={(event) => setForm({ ...form, visible: event.target.checked })} />导航可见</label>
            <label><input className="mr-2" type="checkbox" checked={form.status === "ENABLED"} onChange={(event) => setForm({ ...form, status: event.target.checked ? "ENABLED" : "DISABLED" })} />启用</label>
          </div>
          {save.error && <p className="text-sm text-red-600">{save.error.message}</p>}
          <Button disabled={!form.name.trim() || save.isPending} onClick={() => save.mutate()}>保存</Button>
        </div>
      </Modal>
      <ApiRelation resource={relation} onClose={() => setRelation(undefined)} />
    </>
  );
}

function ApiRelation({ resource, onClose }: { resource?: Resource; onClose: () => void }) {
  const apis = useQuery({ queryKey: ["apis", "relation"], queryFn: getAllApis, enabled: Boolean(resource) });
  const current = useQuery({ queryKey: ["resource-api-ids", resource?.id], queryFn: () => getResourceApiIds(resource!.id), enabled: Boolean(resource) });
  const [selected, setSelected] = useState<Set<number>>();
  useEffect(() => {
    setSelected(current.data ? new Set(current.data) : undefined);
  }, [resource?.id, current.data]);
  const save = useMutation({ mutationFn: () => saveResourceApis(resource!.id, [...(selected ?? [])]), onSuccess: onClose });
  const queryError = apis.error ?? current.error;

  return (
    <Modal title={`关联接口${resource ? ` · ${resource.name}` : ""}`} open={Boolean(resource)} onClose={onClose}>
      <DataState loading={apis.isPending || current.isPending} error={queryError} empty={!apis.data?.length} emptyText="暂无接口数据" onRetry={() => { apis.refetch(); current.refetch(); }}>
        <div className="max-h-96 overflow-auto rounded border border-neutral-200">
          {(apis.data ?? []).map((api) => <label key={api.id} className="flex gap-3 border-b border-neutral-100 p-3 text-sm"><input type="checkbox" checked={(selected ?? new Set()).has(api.id)} onChange={(event) => { const next = new Set(selected ?? []); if (event.target.checked) next.add(api.id); else next.delete(api.id); setSelected(next); }} /><span><b>{api.name}</b><span className="ml-2 text-xs text-neutral-500">{api.path}</span></span></label>)}
        </div>
      </DataState>
      {save.error && <p className="mt-3 text-sm text-red-600">{save.error.message}</p>}
      <Button className="mt-4 w-full" disabled={selected === undefined || apis.isPending || current.isPending || Boolean(queryError) || save.isPending} onClick={() => save.mutate()}>保存关联</Button>
    </Modal>
  );
}
