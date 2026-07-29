"use client";

import { LogOut, Menu } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { logout } from "@/lib/auth";
import type { CurrentEmployee } from "@/types/api";

interface AppHeaderProps {
  employee: CurrentEmployee;
  onOpenNavigation: () => void;
}

export function AppHeader({ employee, onOpenNavigation }: AppHeaderProps) {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  async function handleLogout(): Promise<void> {
    setSubmitting(true);
    try {
      await logout();
      router.replace("/login");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <header className="sticky top-0 z-20 flex h-14 items-center border-b border-neutral-200 bg-white px-4 sm:px-6">
      <button
        type="button"
        aria-label="打开侧栏"
        className="mr-3 rounded-md p-2 text-neutral-600 hover:bg-neutral-100 lg:hidden"
        onClick={onOpenNavigation}
      >
        <Menu className="size-5" />
      </button>
      <div className="ml-auto flex min-w-0 items-center gap-3">
        <div className="min-w-0 text-right">
          <p className="truncate text-sm font-medium text-neutral-900">{employee.name}</p>
          <p className="truncate text-xs text-neutral-500">{employee.username}</p>
        </div>
        <Button
          aria-label="退出登录"
          className="size-9 bg-white p-0 text-neutral-600 ring-1 ring-neutral-200 hover:bg-neutral-100"
          disabled={submitting}
          onClick={handleLogout}
          title="退出登录"
        >
          <LogOut className="size-4" />
        </Button>
      </div>
    </header>
  );
}
