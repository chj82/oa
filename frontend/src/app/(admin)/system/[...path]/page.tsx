import { notFound } from "next/navigation";
import { ApiCatalogPage } from "@/features/system/api-catalog/api-catalog-page";
import { DepartmentsPage } from "@/features/system/departments/departments-page";
import { EmployeesPage } from "@/features/system/employees/employees-page";
import { ResourcesPage } from "@/features/system/resources/resources-page";
import { RolesPage } from "@/features/system/roles/roles-page";

interface SystemPageProps { params: Promise<{ path: string[] }> }

export default async function SystemPage({ params }: SystemPageProps) {
  const key = (await params).path.join("/");
  if (key === "employees") return <EmployeesPage />;
  if (key === "departments") return <DepartmentsPage />;
  if (key === "roles") return <RolesPage />;
  if (key === "resources") return <ResourcesPage />;
  if (key === "apis") return <ApiCatalogPage />;
  notFound();
}
