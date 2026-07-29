"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { RotateCw } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { AppHeader } from "@/components/layout/app-header";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { useCurrentEmployee } from "@/features/auth/use-current-employee";
import { buildNavigation } from "@/lib/permissions";
import { createQueryClient } from "@/lib/query-client";

function AuthenticatedShell({ children }: { children: ReactNode }) {
  const [navigationOpen, setNavigationOpen] = useState(false);
  const { data: employee, isPending, error, refetch, isFetching } = useCurrentEmployee();

  if (isPending) {
    return <main className="grid min-h-screen place-items-center text-sm text-neutral-500">正在加载工作区…</main>;
  }
  if (error || !employee) {
    return (
      <main className="grid min-h-screen place-items-center bg-neutral-50 p-6">
        <div className="max-w-sm text-center">
          <h1 className="text-base font-semibold text-neutral-900">工作区加载失败</h1>
          <p className="mt-2 text-sm text-neutral-600">{error?.message || "未获取到当前员工信息"}</p>
          <Button className="mt-4" disabled={isFetching} onClick={() => refetch()}>
            <RotateCw className="size-4" />
            重新加载
          </Button>
        </div>
      </main>
    );
  }

  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-950">
      <AppSidebar
        navigation={buildNavigation(employee.resources)}
        open={navigationOpen}
        onClose={() => setNavigationOpen(false)}
      />
      <div className="min-w-0 lg:pl-64">
        <AppHeader employee={employee} onOpenNavigation={() => setNavigationOpen(true)} />
        <main className="p-4 sm:p-6">{children}</main>
      </div>
    </div>
  );
}

export function AdminShell({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <AuthenticatedShell>{children}</AuthenticatedShell>
    </QueryClientProvider>
  );
}
