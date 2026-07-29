"use client";

import { Building2, ChevronRight, LayoutDashboard, Settings, Users, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentType } from "react";
import { cn } from "@/lib/utils";
import type { NavigationItem } from "@/lib/permissions";

const icons: Record<string, ComponentType<{ className?: string }>> = {
  Settings,
  Users,
  Building2,
};

interface AppSidebarProps {
  navigation: NavigationItem[];
  open: boolean;
  onClose: () => void;
}

function NavigationEntry({ item, onNavigate }: { item: NavigationItem; onNavigate: () => void }) {
  const pathname = usePathname();
  const Icon = (item.icon && icons[item.icon]) || ChevronRight;
  const content = (
    <span className="flex min-h-9 items-center gap-2 px-3 py-2 text-sm">
      <Icon className="size-4 shrink-0 text-neutral-500" />
      <span className="truncate">{item.name}</span>
    </span>
  );

  return (
    <li>
      {item.clickable && item.path ? (
        <Link
          href={item.path}
          onClick={onNavigate}
          className={cn(
            "block rounded-md text-neutral-700 hover:bg-neutral-100 hover:text-neutral-950",
            pathname === item.path && "bg-neutral-100 font-medium text-neutral-950",
          )}
        >
          {content}
        </Link>
      ) : (
        <div className="font-medium text-neutral-700">{content}</div>
      )}
      {item.children.length > 0 && (
        <ul className="ml-5 border-l border-neutral-200 pl-2">
          {item.children.map((child) => (
            <NavigationEntry key={child.id} item={child} onNavigate={onNavigate} />
          ))}
        </ul>
      )}
    </li>
  );
}

export function AppSidebar({ navigation, open, onClose }: AppSidebarProps) {
  return (
    <>
      {open && (
        <button
          type="button"
          aria-label="关闭导航"
          className="fixed inset-0 z-30 bg-black/30 lg:hidden"
          onClick={onClose}
        />
      )}
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-neutral-200 bg-white transition-transform lg:translate-x-0",
          open ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="flex h-14 items-center gap-2 border-b border-neutral-200 px-4">
          <Building2 aria-hidden="true" className="size-5 text-neutral-800" />
          <span className="font-semibold text-neutral-950">OA 管理系统</span>
          <button
            type="button"
            aria-label="关闭侧栏"
            className="ml-auto rounded-md p-1.5 text-neutral-500 hover:bg-neutral-100 lg:hidden"
            onClick={onClose}
          >
            <X className="size-5" />
          </button>
        </div>
        <nav aria-label="主导航" className="flex-1 overflow-y-auto p-3">
          <ul className="space-y-1">
            <li>
              <Link
                href="/dashboard"
                onClick={onClose}
                className="flex min-h-9 items-center gap-2 rounded-md px-3 py-2 text-sm text-neutral-700 hover:bg-neutral-100"
              >
                <LayoutDashboard className="size-4 text-neutral-500" />
                工作台
              </Link>
            </li>
            {navigation.map((item) => (
              <NavigationEntry key={item.id} item={item} onNavigate={onClose} />
            ))}
          </ul>
        </nav>
      </aside>
    </>
  );
}
