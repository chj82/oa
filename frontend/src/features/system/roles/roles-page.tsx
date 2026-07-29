"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, ShieldCheck, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Permission } from "@/components/permission";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useCurrentEmployee } from "@/features/auth/use-current-employee";
import {
  changeRoleStatus,
  createRole,
  deleteRole,
  getResourceTree,
  getRoleResourceIds,
  saveRoleResources,
  searchRoles,
  updateRole,
} from "@/features/system/system-api";
import {
  ConfirmButton,
  DataState,
  fieldClass,
  labelClass,
  Modal,
  PageHeader,
  Pagination,
  StatusBadge,
} from "@/features/system/system-ui";
import { TreeChecklist } from "@/features/system/tree-view";
import type { Role } from "@/types/api";

export function RolesPage() {
  const client = useQueryClient();
  const current = useCurrentEmployee().data;
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [editing, setEditing] = useState<Role | null>();
  const [authRole, setAuthRole] = useState<Role>();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState<"ENABLED" | "DISABLED">("ENABLED");
  const query = useQuery({
    queryKey: ["roles", page, keyword],
    queryFn: () => searchRoles({ page, size: 20, keyword }),
  });
  const refresh = () => client.invalidateQueries({ queryKey: ["roles"] });
  const save = useMutation({
    mutationFn: () =>
      editing
        ? updateRole(editing.id, { code, name, description, status })
        : createRole({ code, name, description, status }),
    onSuccess: () => {
      setEditing(undefined);
      refresh();
    },
  });
  const action = useMutation({
    mutationFn: (run: () => Promise<void>) => run(),
    onSuccess: refresh,
  });
  const open = (role: Role | null) => {
    setEditing(role);
    setCode(role?.code ?? "");
    setName(role?.name ?? "");
    setDescription(role?.description ?? "");
    setStatus(role?.status ?? "ENABLED");
  };
  const permission = (actionCode: string, child: React.ReactNode) => (
    <Permission
      action={actionCode}
      resources={current?.resources}
      superuser={current?.superuser}
    >
      {child}
    </Permission>
  );
  const records = query.data?.records ?? [];

  return (
    <>
      <PageHeader
        title="角色管理"
        actions={
          <>
            <Input
              aria-label="搜索角色"
              className="w-56"
              placeholder="角色名称或编码"
              value={keyword}
              onChange={(event) => {
                setKeyword(event.target.value);
                setPage(1);
              }}
            />
            {permission(
              "system:role:create",
              <Button onClick={() => open(null)}>
                <Plus className="size-4" />新增角色
              </Button>,
            )}
          </>
        }
      />
      <div className="overflow-hidden rounded-md border border-neutral-200 bg-white">
        <DataState
          loading={query.isPending}
          error={query.error}
          empty={!records.length}
          emptyText="暂无角色数据"
          onRetry={() => query.refetch()}
        >
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-neutral-50 text-neutral-600">
                <tr>
                  <th className="px-4 py-3">角色</th>
                  <th className="px-4 py-3">描述</th>
                  <th className="px-4 py-3">状态</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((role) => (
                  <tr key={role.id} className="border-t border-neutral-100">
                    <td className="px-4 py-3">
                      <b>{role.name}</b>
                      <div className="text-xs text-neutral-500">{role.code}</div>
                    </td>
                    <td className="px-4 py-3 text-neutral-600">
                      {role.description || "-"}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={role.status} />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1">
                        {permission(
                          "system:role:update",
                          <Button
                            title="编辑角色"
                            className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200"
                            onClick={() => open(role)}
                          >
                            <Pencil className="size-4" />
                          </Button>,
                        )}
                        {permission(
                          "system:role:resources",
                          <Button
                            title="资源授权"
                            className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200"
                            onClick={() => setAuthRole(role)}
                          >
                            <ShieldCheck className="size-4" />
                          </Button>,
                        )}
                        {permission(
                          "system:role:status",
                          <Button
                            className="h-8 bg-white px-2 text-neutral-700 ring-1 ring-neutral-200"
                            disabled={action.isPending}
                            onClick={() =>
                              action.mutate(() =>
                                changeRoleStatus(
                                  role.id,
                                  role.status === "ENABLED" ? "DISABLED" : "ENABLED",
                                ),
                              )
                            }
                          >
                            {role.status === "ENABLED" ? "停用" : "启用"}
                          </Button>,
                        )}
                        {permission(
                          "system:role:delete",
                          <ConfirmButton
                            message={`确认删除角色“${role.name}”？`}
                            className="size-8 bg-white p-0 text-red-600 ring-1 ring-neutral-200"
                            disabled={action.isPending}
                            onConfirm={() => action.mutate(() => deleteRole(role.id))}
                          >
                            <Trash2 className="size-4" />
                          </ConfirmButton>,
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DataState>
        {query.data && (
          <Pagination page={page} size={20} total={query.data.total} onPage={setPage} />
        )}
      </div>
      {action.error && <p className="mt-3 text-sm text-red-600">{action.error.message}</p>}
      <Modal
        title={editing ? "编辑角色" : "新增角色"}
        open={editing !== undefined}
        onClose={() => setEditing(undefined)}
      >
        <div className="grid gap-4">
          <label className={labelClass}>
            角色编码<Input value={code} onChange={(event) => setCode(event.target.value)} />
          </label>
          <label className={labelClass}>
            角色名称<Input value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label className={labelClass}>
            描述
            <textarea
              className={`${fieldClass} h-24 py-2`}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>
          <label className={labelClass}>
            状态
            <select
              className={fieldClass}
              value={status}
              onChange={(event) => setStatus(event.target.value as typeof status)}
            >
              <option value="ENABLED">启用</option>
              <option value="DISABLED">停用</option>
            </select>
          </label>
          {save.error && <p className="text-sm text-red-600">{save.error.message}</p>}
          <Button disabled={!code.trim() || !name.trim() || save.isPending} onClick={() => save.mutate()}>
            保存
          </Button>
        </div>
      </Modal>
      <RoleAuthorization role={authRole} onClose={() => setAuthRole(undefined)} />
    </>
  );
}

function RoleAuthorization({ role, onClose }: { role?: Role; onClose: () => void }) {
  const tree = useQuery({
    queryKey: ["resources", "tree"],
    queryFn: getResourceTree,
    enabled: Boolean(role),
  });
  const existing = useQuery({
    queryKey: ["role-resource-ids", role?.id],
    queryFn: () => getRoleResourceIds(role!.id),
    enabled: Boolean(role),
  });
  const [selected, setSelected] = useState<Set<number>>();
  useEffect(() => {
    setSelected(existing.data ? new Set(existing.data) : undefined);
  }, [role?.id, existing.data]);
  const save = useMutation({
    mutationFn: () => saveRoleResources(role!.id, [...(selected ?? [])]),
    onSuccess: onClose,
  });
  const queryError = tree.error ?? existing.error;

  return (
    <Modal title={`资源授权${role ? ` · ${role.name}` : ""}`} open={Boolean(role)} onClose={onClose}>
      <DataState
        loading={tree.isPending || existing.isPending}
        error={queryError}
        empty={!tree.data?.length}
        emptyText="暂无资源数据"
        onRetry={() => {
          tree.refetch();
          existing.refetch();
        }}
      >
        <TreeChecklist
          nodes={tree.data ?? []}
          selected={selected ?? new Set<number>()}
          onChange={setSelected}
        />
      </DataState>
      {save.error && <p className="mt-3 text-sm text-red-600">{save.error.message}</p>}
      <Button
        className="mt-4 w-full"
        disabled={selected === undefined || Boolean(queryError) || tree.isPending || existing.isPending || save.isPending}
        onClick={() => save.mutate()}
      >
        保存授权
      </Button>
    </Modal>
  );
}
