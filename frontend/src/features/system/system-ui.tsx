"use client";

import { AlertCircle, ChevronLeft, ChevronRight, LoaderCircle, X } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/lib/api-client";
import { cn } from "@/lib/utils";
import type { SystemStatus } from "@/types/api";

export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex min-h-10 flex-wrap items-center justify-between gap-3 border-b border-neutral-200 pb-4">
      <h1 className="text-xl font-semibold text-neutral-950">{title}</h1>
      <div className="flex items-center gap-2">{actions}</div>
    </div>
  );
}

export function DataState({ loading, error, empty, emptyText, onRetry, children }: {
  loading: boolean; error: Error | null; empty: boolean; emptyText: string; onRetry: () => void; children: ReactNode;
}) {
  if (loading) return <div className="grid min-h-52 place-items-center text-sm text-neutral-500"><LoaderCircle className="mr-2 inline size-4 animate-spin" />正在加载{emptyText.replace("暂无", "").replace("数据", "数据")}…</div>;
  if (error instanceof ApiClientError && error.status === 403) return <div className="grid min-h-52 place-items-center text-sm text-neutral-500">暂无权限访问</div>;
  if (error) return <div className="grid min-h-52 place-items-center text-center"><div><AlertCircle className="mx-auto size-5 text-red-600"/><p className="mt-2 text-sm text-neutral-700">{error.message}</p><Button className="mt-3" onClick={onRetry}>重新加载</Button></div></div>;
  if (empty) return <div className="grid min-h-52 place-items-center text-sm text-neutral-500">{emptyText}</div>;
  return children;
}

export function StatusBadge({ status }: { status: SystemStatus }) {
  return <span className={cn("inline-flex rounded px-2 py-0.5 text-xs", status === "ENABLED" ? "bg-emerald-50 text-emerald-700" : "bg-neutral-100 text-neutral-600")}>{status === "ENABLED" ? "启用" : "停用"}</span>;
}

export function Modal({ title, open, onClose, children }: { title: string; open: boolean; onClose: () => void; children: ReactNode }) {
  if (!open) return null;
  return <div className="fixed inset-0 z-50 grid place-items-center bg-black/30 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section role="dialog" aria-modal="true" aria-label={title} className="max-h-[90vh] w-full max-w-xl overflow-auto rounded-md bg-white shadow-xl"><header className="sticky top-0 flex items-center justify-between border-b border-neutral-200 bg-white px-5 py-4"><h2 className="font-semibold">{title}</h2><button type="button" aria-label="关闭" className="rounded p-1 text-neutral-500 hover:bg-neutral-100" onClick={onClose}><X className="size-4"/></button></header><div className="p-5">{children}</div></section></div>;
}

export const fieldClass = "h-9 w-full rounded-md border border-neutral-300 bg-white px-3 text-sm outline-none focus:border-neutral-600 focus:ring-2 focus:ring-neutral-200";
export const labelClass = "grid gap-1.5 text-sm font-medium text-neutral-700";

export function Pagination({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (page: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size));
  return <div className="flex items-center justify-end gap-2 border-t border-neutral-200 px-4 py-3 text-sm text-neutral-600"><span>共 {total} 条</span><Button aria-label="上一页" className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200 hover:bg-neutral-100" disabled={page <= 1} onClick={() => onPage(page - 1)}><ChevronLeft className="size-4"/></Button><span>{page} / {pages}</span><Button aria-label="下一页" className="size-8 bg-white p-0 text-neutral-700 ring-1 ring-neutral-200 hover:bg-neutral-100" disabled={page >= pages} onClick={() => onPage(page + 1)}><ChevronRight className="size-4"/></Button></div>;
}

export function ConfirmButton({ children, message, onConfirm, className, disabled }: { children: ReactNode; message: string; onConfirm: () => void; className?: string; disabled?: boolean }) {
  return <Button aria-label={message} className={className} disabled={disabled} onClick={() => { if (window.confirm(message)) onConfirm(); }}>{children}</Button>;
}
