export interface ApiResult<T> {
  success: boolean;
  code: number;
  message: string;
  details: T;
}

export type ResourceType = "DIRECTORY" | "MENU" | "ACTION";
export type ResourceStatus = "ENABLED" | "DISABLED";

export interface Resource {
  id: number;
  parentId: number | null;
  type: ResourceType;
  name: string;
  code: string | null;
  path: string | null;
  icon: string | null;
  sortOrder: number;
  visible: boolean;
  status: ResourceStatus;
  children?: Resource[];
}

export interface CurrentEmployee {
  id: number;
  username: string;
  name: string;
  superuser: boolean;
  resources: Resource[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export type SystemStatus = "ENABLED" | "DISABLED";

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface PageQuery {
  page: number;
  size: number;
}

export interface Department {
  id: number;
  parentId: number;
  name: string;
  sortOrder: number;
  status: SystemStatus;
  children?: Department[];
}

export interface Employee {
  id: number;
  username: string;
  name: string;
  phone: string | null;
  email: string | null;
  departmentId: number;
  status: SystemStatus;
  superuser: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string | null;
  status: SystemStatus;
  createdAt: string;
  updatedAt: string;
}

export interface SystemApi {
  id: number;
  name: string;
  path: string;
  description: string | null;
  status: SystemStatus;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeWriteRequest {
  username: string;
  name: string;
  password?: string;
  phone: string;
  email: string;
  departmentId: number;
  status: SystemStatus;
  superuser: boolean;
}

export interface DepartmentWriteRequest {
  parentId: number;
  name: string;
  sortOrder: number;
  status: SystemStatus;
}

export interface RoleWriteRequest {
  code: string;
  name: string;
  description: string;
  status: SystemStatus;
}

export interface ResourceWriteRequest {
  parentId: number;
  type: ResourceType;
  name: string;
  code: string;
  path: string;
  icon: string;
  sortOrder: number;
  visible: boolean;
  status: SystemStatus;
}
