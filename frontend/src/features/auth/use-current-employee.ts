"use client";

import { useQuery } from "@tanstack/react-query";
import { getCurrentEmployee } from "@/lib/auth";

export function useCurrentEmployee() {
  return useQuery({
    queryKey: ["current-employee"],
    queryFn: getCurrentEmployee,
  });
}
