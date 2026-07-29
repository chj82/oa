import { apiRequest } from "@/lib/api-client";
import type {
  Department,
  DepartmentWriteRequest,
  Employee,
  EmployeeWriteRequest,
  PageQuery,
  PageResult,
  Resource,
  ResourceWriteRequest,
  Role,
  RoleWriteRequest,
  SystemApi,
  SystemStatus,
} from "@/types/api";

function queryString<T extends object>(values: T): string {
  const params = new URLSearchParams();
  Object.entries(values as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined && value !== "") {
      params.set(key, String(value));
    }
  });
  return params.toString();
}

function post<T>(path: string, body?: object): Promise<T> {
  return apiRequest<T>(path, {
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
  });
}

export interface EmployeeQuery extends PageQuery {
  keyword?: string;
  departmentId?: number;
  status?: SystemStatus;
}

export const searchEmployees = (query: EmployeeQuery) =>
  apiRequest<PageResult<Employee>>(`/api/system/employees/page?${queryString(query)}`);
export const getEmployee = (id: number) =>
  apiRequest<Employee>(`/api/system/employees/detail?id=${id}`);
export const createEmployee = (request: EmployeeWriteRequest & { password: string }) =>
  post<Employee>("/api/system/employees/create", request);
export const updateEmployee = (id: number, request: EmployeeWriteRequest) =>
  post<Employee>("/api/system/employees/update", { id, ...request, password: undefined });
export const changeEmployeeStatus = (id: number, status: SystemStatus) =>
  post<void>("/api/system/employees/status", { id, status });
export const deleteEmployee = (id: number) =>
  post<void>(`/api/system/employees/delete?id=${id}`);
export const resetEmployeePassword = (id: number, password: string) =>
  post<void>("/api/system/employees/reset-password", { id, password });
export const getEmployeeRoleIds = (id: number) =>
  apiRequest<number[]>(`/api/system/employees/role-ids?id=${id}`);
export const saveEmployeeRoles = (id: number, ids: number[]) =>
  post<void>(`/api/system/employees/roles?id=${id}`, { ids });

export const getDepartmentTree = () =>
  apiRequest<Department[]>("/api/system/departments/tree");
export const createDepartment = (request: DepartmentWriteRequest) =>
  post<Department>("/api/system/departments/create", request);
export const updateDepartment = (id: number, request: DepartmentWriteRequest) =>
  post<Department>("/api/system/departments/update", { id, ...request });
export const changeDepartmentStatus = (id: number, status: SystemStatus) =>
  post<void>("/api/system/departments/status", { id, status });
export const deleteDepartment = (id: number) =>
  post<void>(`/api/system/departments/delete?id=${id}`);

export interface RoleQuery extends PageQuery {
  keyword?: string;
  status?: SystemStatus;
}
export const searchRoles = (query: RoleQuery) =>
  apiRequest<PageResult<Role>>(`/api/system/roles/page?${queryString(query)}`);
export async function getAllRoles(): Promise<Role[]> {
  const first = await searchRoles({ page: 1, size: 200 });
  const pages = Math.ceil(first.total / first.size);
  if (pages <= 1) return first.records;
  const rest = await Promise.all(
    Array.from({ length: pages - 1 }, (_, index) =>
      searchRoles({ page: index + 2, size: 200 }),
    ),
  );
  return [...first.records, ...rest.flatMap((page) => page.records)];
}
export const createRole = (request: RoleWriteRequest) =>
  post<Role>("/api/system/roles/create", request);
export const updateRole = (id: number, request: RoleWriteRequest) =>
  post<Role>("/api/system/roles/update", { id, ...request });
export const changeRoleStatus = (id: number, status: SystemStatus) =>
  post<void>("/api/system/roles/status", { id, status });
export const deleteRole = (id: number) => post<void>(`/api/system/roles/delete?id=${id}`);
export const getRoleResourceIds = (roleId: number) =>
  apiRequest<number[]>(`/api/system/roles/resource-ids?roleId=${roleId}`);
export const saveRoleResources = (id: number, ids: number[]) =>
  post<void>(`/api/system/roles/resources?id=${id}`, { ids });

export const getResourceTree = () => apiRequest<Resource[]>("/api/system/resources/tree");
export const createResource = (request: ResourceWriteRequest) =>
  post<Resource>("/api/system/resources/create", request);
export const updateResource = (id: number, request: ResourceWriteRequest) =>
  post<Resource>("/api/system/resources/update", { id, ...request });
export const changeResourceStatus = (id: number, status: SystemStatus) =>
  post<void>("/api/system/resources/status", { id, status });
export const deleteResource = (id: number) =>
  post<void>(`/api/system/resources/delete?id=${id}`);
export const getResourceApiIds = (resourceId: number) =>
  apiRequest<number[]>(`/api/system/resources/api-ids?resourceId=${resourceId}`);
export const saveResourceApis = (resourceId: number, apiIds: number[]) =>
  post<void>("/api/system/resources/apis", { resourceId, apiIds });

export interface ApiQuery extends PageQuery {
  keyword?: string;
  status?: SystemStatus;
}
export const searchApis = (query: ApiQuery) =>
  apiRequest<PageResult<SystemApi>>(`/api/system/apis/page?${queryString(query)}`);
export async function getAllApis(): Promise<SystemApi[]> {
  const first = await searchApis({ page: 1, size: 200 });
  const pages = Math.ceil(first.total / first.size);
  if (pages <= 1) return first.records;
  const rest = await Promise.all(
    Array.from({ length: pages - 1 }, (_, index) =>
      searchApis({ page: index + 2, size: 200 }),
    ),
  );
  return [...first.records, ...rest.flatMap((page) => page.records)];
}
export const getSystemApi = (id: number) =>
  apiRequest<SystemApi>(`/api/system/apis/detail?id=${id}`);
export const changeSystemApiStatus = (id: number, status: SystemStatus) =>
  post<void>("/api/system/apis/status", { id, status });
