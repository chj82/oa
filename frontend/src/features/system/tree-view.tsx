"use client";

import { useEffect, useRef } from "react";
import { calculateCheckedState, toggleTreeSelection } from "@/features/system/tree-selection";
import { StatusBadge } from "@/features/system/system-ui";
import type { SystemStatus } from "@/types/api";

export interface DisplayTreeNode { id: number; name: string; status: SystemStatus; children?: DisplayTreeNode[] }

export function TreeRows<T extends DisplayTreeNode>({ nodes, renderActions, depth = 0 }: { nodes: T[]; renderActions: (node: T) => React.ReactNode; depth?: number }) {
  return <>{nodes.map((node) => <div key={node.id}><div className="flex min-h-11 items-center border-b border-neutral-100 px-4 text-sm"><span className="min-w-0 flex-1 truncate" style={{ paddingLeft: `${depth * 20}px` }}>{depth > 0 && <span className="mr-2 text-neutral-300">└</span>}{node.name}</span><StatusBadge status={node.status}/><div className="ml-4 flex gap-1">{renderActions(node)}</div></div><TreeRows nodes={(node.children ?? []) as T[]} renderActions={renderActions} depth={depth + 1}/></div>)}</>;
}

function TreeCheckbox<T extends DisplayTreeNode>({ nodes, node, selected, onChange }: { nodes: T[]; node: T; selected: Set<number>; onChange: (next: Set<number>) => void }) {
  const ref = useRef<HTMLInputElement>(null); const state = calculateCheckedState(nodes, selected, node.id);
  useEffect(() => { if (ref.current) ref.current.indeterminate = state === "indeterminate"; }, [state]);
  return <div><label className="flex min-h-9 items-center gap-2 rounded px-2 text-sm hover:bg-neutral-50"><input ref={ref} type="checkbox" checked={state === "checked"} onChange={() => onChange(toggleTreeSelection(nodes, selected, node.id))}/><span>{node.name}</span></label><div className="ml-5">{(node.children ?? []).map((child) => <TreeCheckbox key={child.id} nodes={nodes} node={child as T} selected={selected} onChange={onChange}/>)}</div></div>;
}

export function TreeChecklist<T extends DisplayTreeNode>({ nodes, selected, onChange }: { nodes: T[]; selected: Set<number>; onChange: (next: Set<number>) => void }) { return <div className="max-h-96 overflow-auto rounded border border-neutral-200 p-2">{nodes.map((node) => <TreeCheckbox key={node.id} nodes={nodes} node={node} selected={selected} onChange={onChange}/>)}</div>; }
