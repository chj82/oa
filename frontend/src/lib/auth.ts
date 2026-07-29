import { apiRequest } from "@/lib/api-client";
import type { CurrentEmployee, LoginRequest } from "@/types/api";

export function login(request: LoginRequest): Promise<void> {
  return apiRequest<void>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function logout(): Promise<void> {
  return apiRequest<void>("/api/auth/logout", { method: "POST" });
}

export function getCurrentEmployee(): Promise<CurrentEmployee> {
  return apiRequest<CurrentEmployee>("/api/auth/current");
}
