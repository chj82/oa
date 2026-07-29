import type { Resource } from "@/types/api";

export interface NavigationItem {
  id: number;
  name: string;
  path: string | null;
  icon: string | null;
  clickable: boolean;
  children: NavigationItem[];
}

function compareResources(left: Resource, right: Resource): number {
  return left.sortOrder - right.sortOrder || left.id - right.id;
}

function toNavigation(resource: Resource): NavigationItem | null {
  if (
    resource.status !== "ENABLED" ||
    !resource.visible ||
    (resource.type !== "DIRECTORY" && resource.type !== "MENU")
  ) {
    return null;
  }
  const children = (resource.children ?? [])
    .slice()
    .sort(compareResources)
    .map(toNavigation)
    .filter((item): item is NavigationItem => item !== null);

  return {
    id: resource.id,
    name: resource.name,
    path: resource.path,
    icon: resource.icon,
    clickable: resource.type === "MENU" && Boolean(resource.path),
    children,
  };
}

export function buildNavigation(resources?: Resource[]): NavigationItem[] {
  return (resources ?? [])
    .slice()
    .sort(compareResources)
    .map(toNavigation)
    .filter((item): item is NavigationItem => item !== null);
}

export function hasAction(resources: Resource[] | undefined, action: string): boolean {
  return (resources ?? []).some(
    (resource) =>
      (resource.type === "ACTION" &&
        resource.status === "ENABLED" &&
        resource.code === action) ||
      hasAction(resource.children, action),
  );
}
