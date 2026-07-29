"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { login } from "@/lib/auth";
import type { LoginRequest } from "@/types/api";

const loginSchema = z.object({
  username: z.string().trim().min(1, "请输入用户名"),
  password: z.string().min(6, "密码至少 6 位"),
});

interface LoginFormProps {
  onSuccess: () => void;
  request?: (values: LoginRequest) => Promise<void>;
}

export function LoginForm({ onSuccess, request = login }: LoginFormProps) {
  const [serverError, setServerError] = useState<string>();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginRequest>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" },
  });

  async function submit(values: LoginRequest): Promise<void> {
    setServerError(undefined);
    try {
      await request(values);
      onSuccess();
    } catch (error) {
      setServerError(error instanceof Error ? error.message : "登录失败，请稍后重试");
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit(submit)} noValidate>
      <div className="space-y-1.5">
        <label className="text-sm font-medium text-neutral-800" htmlFor="username">
          用户名
        </label>
        <Input id="username" autoComplete="username" {...register("username")} />
        {errors.username && <p className="text-sm text-red-700">{errors.username.message}</p>}
      </div>
      <div className="space-y-1.5">
        <label className="text-sm font-medium text-neutral-800" htmlFor="password">
          密码
        </label>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          {...register("password")}
        />
        {errors.password && <p className="text-sm text-red-700">{errors.password.message}</p>}
      </div>
      {serverError && (
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {serverError}
        </p>
      )}
      <Button className="w-full" type="submit" disabled={isSubmitting}>
        {isSubmitting && <LoaderCircle aria-hidden="true" className="size-4 animate-spin" />}
        {isSubmitting ? "登录中" : "登录"}
      </Button>
    </form>
  );
}
