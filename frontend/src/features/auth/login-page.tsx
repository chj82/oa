"use client";

import { Building2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { LoginForm } from "@/features/auth/login-form";

export function LoginPage() {
  const router = useRouter();

  return (
    <main className="flex min-h-screen items-center justify-center bg-neutral-100 px-4 py-8">
      <section className="w-full max-w-sm rounded-lg border border-neutral-200 bg-white p-6 shadow-sm">
        <div className="mb-6 flex items-center gap-3 border-b border-neutral-100 pb-5">
          <span className="flex size-9 items-center justify-center rounded-md bg-neutral-900 text-white">
            <Building2 aria-hidden="true" className="size-5" />
          </span>
          <div>
            <h1 className="text-lg font-semibold text-neutral-950">OA 管理系统</h1>
            <p className="mt-0.5 text-sm text-neutral-500">员工登录</p>
          </div>
        </div>
        <LoginForm onSuccess={() => router.replace("/dashboard")} />
      </section>
    </main>
  );
}
