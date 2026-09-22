"use client"

import { useState } from "react"
import Link from "next/link"
import { Search, SlidersHorizontal, ChevronDown, MoreHorizontal, FolderInput, Tag, Trash2, Terminal } from "lucide-react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Badge } from "@/components/ui/badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { StatusDot, PlatformIcon, ProtocolBadge } from "@/components/console/status"
import { assets } from "@/lib/mock-data"
import { cn } from "@/lib/utils"

export function AssetsTable() {
  const [selected, setSelected] = useState<string[]>([])
  const allSelected = selected.length === assets.length
  const someSelected = selected.length > 0

  const toggle = (id: string) =>
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))
  const toggleAll = () => setSelected(allSelected ? [] : assets.map((a) => a.id))

  return (
    <div className="flex flex-col">
      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-2 border-b border-border p-3">
        <div className="relative min-w-[200px] flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input placeholder="Search assets..." className="h-9 pl-9" />
        </div>
        <Select defaultValue="all">
          <SelectTrigger className="h-9 w-[130px]">
            <SelectValue placeholder="Platform" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Platforms</SelectItem>
            <SelectItem value="linux">Linux</SelectItem>
            <SelectItem value="windows">Windows</SelectItem>
            <SelectItem value="db">Database</SelectItem>
          </SelectContent>
        </Select>
        <Select defaultValue="all">
          <SelectTrigger className="h-9 w-[120px]">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Status</SelectItem>
            <SelectItem value="online">Online</SelectItem>
            <SelectItem value="offline">Offline</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="sm" className="h-9 gap-1.5">
          <SlidersHorizontal className="h-4 w-4" /> Columns
        </Button>
      </div>

      {/* Bulk actions bar */}
      {someSelected && (
        <div className="flex items-center gap-2 border-b border-border bg-accent px-3 py-2">
          <span className="text-sm font-medium text-accent-foreground">{selected.length} items selected</span>
          <div className="ml-auto flex items-center gap-1">
            <Button variant="ghost" size="sm" className="h-8 gap-1.5">
              <FolderInput className="h-4 w-4" /> Move Node
            </Button>
            <Button variant="ghost" size="sm" className="h-8 gap-1.5">
              <Tag className="h-4 w-4" /> Add Label
            </Button>
            <Button variant="ghost" size="sm" className="h-8 gap-1.5 text-destructive hover:text-destructive">
              <Trash2 className="h-4 w-4" /> Delete
            </Button>
          </div>
        </div>
      )}

      {/* Table */}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10">
              <Checkbox checked={allSelected} onCheckedChange={toggleAll} aria-label="Select all" />
            </TableHead>
            <TableHead>Name</TableHead>
            <TableHead>IP Address</TableHead>
            <TableHead>Platform</TableHead>
            <TableHead>Node</TableHead>
            <TableHead className="text-center">Accounts</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {assets.map((a) => (
            <TableRow key={a.id} data-state={selected.includes(a.id) ? "selected" : undefined}>
              <TableCell>
                <Checkbox
                  checked={selected.includes(a.id)}
                  onCheckedChange={() => toggle(a.id)}
                  aria-label={`Select ${a.name}`}
                />
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  <span className="font-medium">{a.name}</span>
                  {a.protocols.map((p) => (
                    <ProtocolBadge key={p} protocol={p} />
                  ))}
                </div>
              </TableCell>
              <TableCell className="font-mono text-sm text-muted-foreground">{a.ip}</TableCell>
              <TableCell>
                <span className="flex items-center gap-1.5">
                  <PlatformIcon platform={a.platform} />
                  <span className="text-sm">{a.platform}</span>
                </span>
              </TableCell>
              <TableCell className="font-mono text-xs text-muted-foreground">{a.node}</TableCell>
              <TableCell className="text-center text-sm">{a.accounts}</TableCell>
              <TableCell>
                <StatusDot status={a.status} label />
              </TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end gap-1">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button
                        size="sm"
                        className={cn("h-8 gap-1.5", a.status === "offline" && "pointer-events-none opacity-50")}
                      >
                        <Terminal className="h-3.5 w-3.5" /> Connect
                        <ChevronDown className="h-3.5 w-3.5" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      {a.protocols.map((p) => (
                        <DropdownMenuItem key={p} asChild>
                          <Link href="/connect/ssh/demo-token">Connect via {p}</Link>
                        </DropdownMenuItem>
                      ))}
                    </DropdownMenuContent>
                  </DropdownMenu>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon" className="h-8 w-8">
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem>Edit</DropdownMenuItem>
                      <DropdownMenuItem>Move Node</DropdownMenuItem>
                      <DropdownMenuItem>Test Connectivity</DropdownMenuItem>
                      <DropdownMenuItem className="text-destructive">Delete</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {/* Footer / pagination */}
      <div className="flex items-center justify-between border-t border-border p-3">
        <span className="text-sm text-muted-foreground">1-{assets.length} of 340</span>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled>
            Previous
          </Button>
          <Badge variant="secondary">Page 1</Badge>
          <Button variant="outline" size="sm">
            Next
          </Button>
        </div>
      </div>
    </div>
  )
}
