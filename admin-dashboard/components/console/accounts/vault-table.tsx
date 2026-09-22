"use client"

import { useState } from "react"
import { Eye, RotateCw, History, AlertTriangle } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { vaultAccounts, type VaultAccount } from "@/lib/mock-data"
import { RevealModal } from "./reveal-modal"
import { cn } from "@/lib/utils"

function StatusBadge({ status }: { status: VaultAccount["status"] }) {
  if (status === "ok")
    return <Badge variant="outline" className="border-0 bg-success/10 text-success">Healthy</Badge>
  if (status === "expiring")
    return (
      <Badge variant="outline" className="gap-1 border-0 bg-warning/10 text-warning">
        <AlertTriangle className="h-3 w-3" /> Expiring Soon
      </Badge>
    )
  return <Badge variant="outline" className="border-0 bg-destructive/10 text-destructive">Expired</Badge>
}

export function VaultTable() {
  const [active, setActive] = useState<VaultAccount | null>(null)
  const [open, setOpen] = useState(false)

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10">
              <Checkbox aria-label="Select all" />
            </TableHead>
            <TableHead>Account</TableHead>
            <TableHead>Asset</TableHead>
            <TableHead>Type</TableHead>
            <TableHead>Last Rotated</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {vaultAccounts.map((v) => (
            <TableRow key={v.id} className={cn(v.status === "expired" && "bg-destructive/5")}>
              <TableCell>
                <Checkbox aria-label={`Select ${v.account}`} />
              </TableCell>
              <TableCell className="font-mono font-medium">{v.account}</TableCell>
              <TableCell className="font-mono text-sm text-muted-foreground">{v.asset}</TableCell>
              <TableCell className="text-sm">{v.type}</TableCell>
              <TableCell className="text-sm text-muted-foreground">{v.lastRotated}</TableCell>
              <TableCell>
                <StatusBadge status={v.status} />
              </TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end gap-1">
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-8 gap-1.5"
                    onClick={() => {
                      setActive(v)
                      setOpen(true)
                    }}
                  >
                    <Eye className="h-3.5 w-3.5" /> Reveal
                  </Button>
                  <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Rotate">
                    <RotateCw className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="History">
                    <History className="h-4 w-4" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <RevealModal account={active} open={open} onOpenChange={setOpen} />
    </>
  )
}
