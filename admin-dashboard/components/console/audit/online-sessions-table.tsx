"use client"

import { useState } from "react"
import Link from "next/link"
import { Eye, Ban } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { toast } from "sonner"
import { ProtocolBadge } from "@/components/console/status"
import { activeSessions, type Session } from "@/lib/mock-data"

export function OnlineSessionsTable() {
  const [target, setTarget] = useState<Session | null>(null)

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>User</TableHead>
            <TableHead>Asset</TableHead>
            <TableHead>Account</TableHead>
            <TableHead>Protocol</TableHead>
            <TableHead>Start</TableHead>
            <TableHead>Duration</TableHead>
            <TableHead>Client IP</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {activeSessions.map((s) => (
            <TableRow key={s.id}>
              <TableCell className="font-medium">{s.user}</TableCell>
              <TableCell className="font-mono text-sm">{s.asset}</TableCell>
              <TableCell className="font-mono text-sm text-muted-foreground">{s.account}</TableCell>
              <TableCell>
                <ProtocolBadge protocol={s.protocol} />
              </TableCell>
              <TableCell className="font-mono text-sm text-muted-foreground">{s.startTime}</TableCell>
              <TableCell className="font-mono text-sm tabular-nums">{s.duration}</TableCell>
              <TableCell className="font-mono text-sm text-muted-foreground">{s.clientIp}</TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end gap-1">
                  <Button variant="outline" size="sm" className="h-8 gap-1.5" asChild>
                    <Link href="/console/audit/sessions/replay">
                      <Eye className="h-3.5 w-3.5" /> Monitor
                    </Link>
                  </Button>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-8 gap-1.5 border-destructive/40 text-destructive hover:bg-destructive hover:text-destructive-foreground"
                        onClick={() => setTarget(s)}
                      >
                        <Ban className="h-3.5 w-3.5" /> Terminate
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Force close session</TooltipContent>
                  </Tooltip>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <AlertDialog open={!!target} onOpenChange={(o) => !o && setTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Terminate session?</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to terminate {target?.user}&apos;s session on {target?.asset}? This action is
              immediate.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                toast.success(`Session terminated`, { description: `${target?.user} on ${target?.asset}` })
                setTarget(null)
              }}
            >
              Terminate
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
