interface TreeNode {
  id: number;
  children?: TreeNode[];
}

export type CheckedState = "checked" | "indeterminate" | "unchecked";

function findNode(nodes: TreeNode[], id: number): TreeNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node;
    const child = findNode(node.children ?? [], id);
    if (child) return child;
  }
  return undefined;
}

function collectIds(node: TreeNode): number[] {
  return [node.id, ...(node.children ?? []).flatMap(collectIds)];
}

function normalize(nodes: TreeNode[], selected: Set<number>): Set<number> {
  const result = new Set(selected);
  const visit = (node: TreeNode): boolean => {
    const children = node.children ?? [];
    if (children.length === 0) return result.has(node.id);
    const allChildrenChecked = children.every(visit);
    if (allChildrenChecked) result.add(node.id);
    else result.delete(node.id);
    return allChildrenChecked;
  };
  nodes.forEach(visit);
  return result;
}

export function toggleTreeSelection<T extends TreeNode>(
  nodes: T[],
  selected: Set<number>,
  id: number,
): Set<number> {
  const node = findNode(nodes, id);
  if (!node) return new Set(selected);
  const result = new Set(selected);
  const ids = collectIds(node);
  const shouldSelect = !ids.every((value) => result.has(value));
  ids.forEach((value) => (shouldSelect ? result.add(value) : result.delete(value)));
  return normalize(nodes, result);
}

export function calculateCheckedState<T extends TreeNode>(
  nodes: T[],
  selected: Set<number>,
  id: number,
): CheckedState {
  const node = findNode(nodes, id);
  if (!node) return "unchecked";
  const ids = collectIds(node);
  const count = ids.filter((value) => selected.has(value)).length;
  if (count === ids.length) return "checked";
  if (count > 0) return "indeterminate";
  return "unchecked";
}
