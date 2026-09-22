"use client"

import { useState } from "react"
import { ChevronRight, Folder, FolderOpen, Search } from "lucide-react"
import { cn } from "@/lib/utils"
import { Input } from "@/components/ui/input"
import { nodeTree, type TreeNode } from "@/lib/mock-data"

function TreeItem({
  node,
  depth,
  selected,
  onSelect,
}: {
  node: TreeNode
  depth: number
  selected: string
  onSelect: (id: string) => void
}) {
  const [open, setOpen] = useState(depth < 2)
  const hasChildren = !!node.children?.length
  const isSelected = selected === node.id

  return (
    <div>
      <button
        onClick={() => {
          onSelect(node.id)
          if (hasChildren) setOpen((o) => !o)
        }}
        className={cn(
          "flex w-full items-center gap-1.5 rounded-md py-1.5 pr-2 text-sm transition-colors",
          isSelected ? "bg-accent font-medium text-accent-foreground" : "hover:bg-secondary",
        )}
        style={{ paddingLeft: depth * 14 + 8 }}
      >
        <ChevronRight
          className={cn(
            "h-3.5 w-3.5 shrink-0 text-muted-foreground transition-transform",
            hasChildren ? "opacity-100" : "opacity-0",
            open && "rotate-90",
          )}
        />
        {open && hasChildren ? (
          <FolderOpen className="h-4 w-4 shrink-0 text-primary" />
        ) : (
          <Folder className="h-4 w-4 shrink-0 text-muted-foreground" />
        )}
        <span className="flex-1 truncate text-left">{node.label}</span>
        <span className="text-xs text-muted-foreground">{node.count}</span>
      </button>
      {open && hasChildren && (
        <div>
          {node.children!.map((c) => (
            <TreeItem key={c.id} node={c} depth={depth + 1} selected={selected} onSelect={onSelect} />
          ))}
        </div>
      )}
    </div>
  )
}

export function NodeTree() {
  const [selected, setSelected] = useState("root")
  return (
    <div className="flex w-full flex-col gap-3">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input placeholder="Search nodes..." className="h-9 pl-9" />
      </div>
      <div className="space-y-0.5">
        {nodeTree.map((n) => (
          <TreeItem key={n.id} node={n} depth={0} selected={selected} onSelect={setSelected} />
        ))}
      </div>
    </div>
  )
}
