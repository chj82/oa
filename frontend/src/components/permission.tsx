import type { ReactNode } from "react";
import { hasAction } from "@/lib/permissions";
import type { Resource } from "@/types/api";

interface PermissionProps {
  action: string;
  resources?: Resource[];
  superuser?: boolean;
  children: ReactNode;
}

export function Permission({
  action,
  resources,
  superuser = false,
  children,
}: PermissionProps) {
  if (!superuser && !hasAction(resources, action)) {
    return null;
  }
  return children;
}
