"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Pencil, Plus, ShieldCheck, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Permission } from "@/components/permission";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useCurrentEmployee } from "@/features/auth/use-current-employee";
import {
  changeEmployeeStatus,
  createEmployee,
  deleteEmployee,
  getAllRoles,
  getDepartmentTree,
  getEmployeeRoleIds,
  resetEmployeePassword,
  saveEmployeeRoles,
  searchEmployees,
  updateEmployee,
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
import type { Department, Employee, EmployeeWriteRequest, Role } from "@/types/api";

const schema = z.object({
  username: z.string().min(1, "请输入用户名").max(64),
  name: z.string().min(1, "请输入姓名").max(100),
  password: z.string(),
  phone: z.string().max(32),
  email: z.string().email("邮箱格式不正确").or(z.literal("")),
  departmentId: z.coerce.number().positive("请选择部门"),
  status: z.enum(["ENABLED", "DISABLED"]),
  superuser: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

const emptyEmployee: FormValues = {
  username: "",
  name: "",
  password: "",
  phone: "",
  email: "",
  departmentId: 0,
  status: "ENABLED",
  superuser: false,
};

function flattenDepartments(
  nodes: Department[],
  depth = 0,
): Array<{ id: number; name: string }> {
  return nodes.flatMap((node) => [
    { id: node.id, name: `${"　".repeat(depth)}${node.name}` },
    ...flattenDepartments(node.children ?? [], depth + 1),
  ]);
}

export function EmployeesPage() {
  const client = useQueryClient();
  const current = useCurrentEmployee().data;
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [editing, setEditing] = useState<Employee | null>();
  const [rolesFor, setRolesFor] = useState<Employee>();
  const [resetFor, setResetFor] = useState<Employee>();
  const employees = useQuery({
    queryKey: ["employees", page, keyword],
    queryFn: () => searchEmployees({ page, size: 20, keyword }),
  });
  const departments = useQuery({
    queryKey: ["departments", "tree"],
    queryFn: getDepartmentTree,
    enabled: editing !== undefined,
  });
  const roles = useQuery({
    queryKey: ["roles", "all"],
    queryFn: getAllRoles,
    enabled: Boolean(rolesFor),
  });
  const invalidate = () => client.invalidateQueries({ queryKey: ["employees"] });
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: emptyEmployee,
  });
  const save = useMutation({
    mutationFn: async (values: FormValues) => {
      const request: EmployeeWriteRequest = { ...values, password: undefined };
      if (editing) return updateEmployee(editing.id, request);
      if (values.password.length < 8) throw new Error("密码至少 8 位");
      return createEmployee({ ...request, password: values.password });
    },
    onSuccess: async () => {
      setEditing(undefined);
      await invalidate();
    },
  });
  const action = useMutation({
    mutationFn: (run: () => Promise<void>) => run(),
    onSuccess: invalidate,
  });
  const openForm = (employee: Employee | null) => {
    setEditing(employee);
    form.reset(
      employee
        ? {
            username: employee.username,
            name: employee.name,
            password: "",
            phone: employee.phone ?? "",
            email: employee.email ?? "",
            departmentId: employee.departmentId,
            status: employee.status,
            superuser: employee.superuser,
          }
        : emptyEmployee,
    );
  };
  const permission = (actionCode: string, child: React.ReactNode) => (
    <Permission action={actionCode} resources={current?.resources} superuser={current?.superuser}>
      {child}
    </Permission>
  );
  const records = employees.data?.records ?? [];

  return (
    <>
      <PageHeader
        title="员工管理"
        actions={
          <>
            <Input
              aria-label="搜索员工"
              className="w-56"
              placeholder="用户名或姓名"
              value={keyword}
              onChange={(event) => {
                setKeyword(event.target.value);
                setPage(1);
              }}
            />
            {permission("system:employee:create", <Button onClick={() => openForm(null)}><Plus className="size-4" />新增员工</Button>)}
          </>
        }
      />
      <div className="overflow-hidden rounded-md border border-neutral-200 bg-white">
        <DataState loading={employees.isPending} error={employees.error} empty={!records.length} emptyText="暂无员工数据" onRetry={() => employees.refetch()}>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-neutral-50 text-neutral-600"><tr><th className="px-4 py-3">员工</th><th className="px-4 py-3">联系方式</th><th className="px-4 py-3">状态</th><th className="px-4 py-3 text-right">操作</th></tr></thead>
              <tbody>{records.map((employee) => <EmployeeRow key={employee.id} employee={employee} actionPending={action.isPending} permission={permission} onEdit={() => openForm(employee)} onRoles={() => setRolesFor(employee)} onReset={() => setResetFor(employee)} onStatus={() => action.mutate(() => changeEmployeeStatus(employee.id, employee.status === "ENABLED" ? "DISABLED" : "ENABLED"))} onDelete={() => action.mutate(() => deleteEmployee(employee.id))} />)}</tbody>
            </table>
          </div>
        </DataState>
        {employees.data && <Pagination page={page} size={20} total={employees.data.total} onPage={setPage} />}
      </div>
      {action.error && <p className="mt-3 text-sm text-red-600">{action.error.message}</p>}
      <EmployeeModal editing={editing} form={form} departments={departments.data ?? []} departmentsLoading={departments.isPending} departmentsError={departments.error} onRetryDepartments={() => departments.refetch()} savePending={save.isPending} saveError={save.error} onClose={() => setEditing(undefined)} onSave={(values) => save.mutate(values)} />
      <RoleAssignment employee={rolesFor} roles={roles.data ?? []} rolesLoading={roles.isPending} rolesError={roles.error} refetchRoles={() => roles.refetch()} onClose={() => setRolesFor(undefined)} onSaved={invalidate} />
      <PasswordReset employee={resetFor} onClose={() => setResetFor(undefined)} />
    </>
  );
}

function EmployeeRow({ employee, actionPending, permission, onEdit, onRoles, onReset, onStatus, onDelete }: { employee: Employee; actionPending: boolean; permission: (code: string, child: React.ReactNode) => React.ReactNode; onEdit: () => void; onRoles: () => void; onReset: () => void; onStatus: () => void; onDelete: () => void }) {
  return <tr className="border-t border-neutral-100"><td className="px-4 py-3"><div className="font-medium">{employee.name}{employee.superuser && <span className="ml-2 text-xs text-amber-700">超级管理员</span>}</div><div className="text-xs text-neutral-500">{employee.username}</div></td><td className="px-4 py-3 text-neutral-600">{employee.phone || employee.email || "-"}</td><td className="px-4 py-3"><StatusBadge status={employee.status} /></td><td className="px-4 py-3"><div className="flex justify-end gap-1">{permission("system:employee:update", <Button title="编辑员工" className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200" onClick={onEdit}><Pencil className="size-4" /></Button>)}{permission("system:employee:roles", <Button title="分配角色" className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200" onClick={onRoles}><ShieldCheck className="size-4" /></Button>)}{permission("system:employee:reset-password", <Button title="重置密码" className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200" onClick={onReset}><KeyRound className="size-4" /></Button>)}{permission("system:employee:status", <Button className="h-8 bg-white px-2 text-neutral-700 ring-1 ring-neutral-200" disabled={actionPending} onClick={onStatus}>{employee.status === "ENABLED" ? "停用" : "启用"}</Button>)}{permission("system:employee:delete", <ConfirmButton message={`确认删除员工“${employee.name}”？`} className="size-8 bg-white p-0 text-red-600 ring-1 ring-neutral-200" disabled={actionPending} onConfirm={onDelete}><Trash2 className="size-4" /></ConfirmButton>)}</div></td></tr>;
}

function EmployeeModal({ editing, form, departments, departmentsLoading, departmentsError, onRetryDepartments, savePending, saveError, onClose, onSave }: { editing: Employee | null | undefined; form: ReturnType<typeof useForm<FormValues>>; departments: Department[]; departmentsLoading: boolean; departmentsError: Error | null; onRetryDepartments: () => void; savePending: boolean; saveError: Error | null; onClose: () => void; onSave: (values: FormValues) => void }) {
  return <Modal title={editing ? "编辑员工" : "新增员工"} open={editing !== undefined} onClose={onClose}><form className="grid gap-4" onSubmit={form.handleSubmit(onSave)}><label className={labelClass}>用户名<Input {...form.register("username")} /></label><label className={labelClass}>姓名<Input {...form.register("name")} /></label>{!editing && <label className={labelClass}>初始密码<Input type="password" {...form.register("password")} /></label>}<div className="grid grid-cols-2 gap-3"><label className={labelClass}>手机号<Input {...form.register("phone")} /></label><label className={labelClass}>邮箱<Input {...form.register("email")} /></label></div><DataState loading={departmentsLoading} error={departmentsError} empty={!departments.length} emptyText="暂无部门数据" onRetry={onRetryDepartments}><label className={labelClass}>部门<select className={fieldClass} {...form.register("departmentId")}><option value={0}>请选择</option>{flattenDepartments(departments).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label></DataState><div className="grid grid-cols-2 gap-3"><label className={labelClass}>状态<select className={fieldClass} {...form.register("status")}><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label><label className="flex items-end gap-2 pb-2 text-sm"><input type="checkbox" {...form.register("superuser")} />超级管理员</label></div>{Object.values(form.formState.errors)[0]?.message && <p className="text-sm text-red-600">{Object.values(form.formState.errors)[0]?.message}</p>}{saveError && <p className="text-sm text-red-600">{saveError.message}</p>}<div className="flex justify-end gap-2"><Button className="bg-white text-neutral-700 ring-1 ring-neutral-200" onClick={onClose}>取消</Button><Button type="submit" disabled={departmentsLoading || Boolean(departmentsError) || savePending}>保存</Button></div></form></Modal>;
}

function RoleAssignment({ employee, roles, rolesLoading, rolesError, refetchRoles, onClose, onSaved }: { employee?: Employee; roles: Role[]; rolesLoading: boolean; rolesError: Error | null; refetchRoles: () => void; onClose: () => void; onSaved: () => void }) {
  const selected = useQuery({ queryKey: ["employee-role-ids", employee?.id], queryFn: () => getEmployeeRoleIds(employee!.id), enabled: Boolean(employee) });
  const [ids, setIds] = useState<number[]>();
  useEffect(() => setIds(selected.data), [employee?.id, selected.data]);
  const mutation = useMutation({ mutationFn: () => saveEmployeeRoles(employee!.id, ids ?? []), onSuccess: () => { onSaved(); onClose(); } });
  const currentIds = ids ?? [];
  return <Modal title="分配角色" open={Boolean(employee)} onClose={onClose}><DataState loading={selected.isPending || rolesLoading} error={selected.error ?? rolesError} empty={!roles.length} emptyText="暂无角色数据" onRetry={() => { selected.refetch(); refetchRoles(); }}><div className="grid gap-2">{roles.map((role) => <label key={role.id} className="flex gap-2 rounded border border-neutral-200 p-3 text-sm"><input type="checkbox" checked={currentIds.includes(role.id)} onChange={(event) => setIds(event.target.checked ? [...currentIds, role.id] : currentIds.filter((id) => id !== role.id))} />{role.name}</label>)}</div></DataState>{mutation.error && <p className="mt-3 text-sm text-red-600">{mutation.error.message}</p>}<Button className="mt-3 w-full" disabled={ids === undefined || selected.isPending || rolesLoading || Boolean(selected.error || rolesError) || mutation.isPending} onClick={() => mutation.mutate()}>保存角色</Button></Modal>;
}

function PasswordReset({ employee, onClose }: { employee?: Employee; onClose: () => void }) {
  const [password, setPassword] = useState("");
  useEffect(() => setPassword(""), [employee?.id]);
  const mutation = useMutation({ mutationFn: () => resetEmployeePassword(employee!.id, password), onSuccess: () => { setPassword(""); onClose(); } });
  return <Modal title="重置密码" open={Boolean(employee)} onClose={() => { setPassword(""); mutation.reset(); onClose(); }}><label className={labelClass}>新密码<Input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>{mutation.error && <p className="mt-3 text-sm text-red-600">{mutation.error.message}</p>}<Button className="mt-4 w-full" disabled={password.length < 8 || mutation.isPending} onClick={() => mutation.mutate()}>确认重置</Button></Modal>;
}
