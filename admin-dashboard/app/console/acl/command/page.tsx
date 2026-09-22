import { Plus } from "lucide-react"
import { PageHeader } from "@/components/console/page-header"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

type Action = "allow" | "reject" | "review" | "warn"

const rules: { id: string; name: string; pattern: string; scope: string; action: Action }[] = [
  { id: "r1", name: "Block recursive delete", pattern: "rm -rf *", scope: "All Linux", action: "reject" },
  { id: "r2", name: "Review privilege escalation", pattern: "sudo su -", scope: "Production", action: "review" },
  { id: "r3", name: "Warn on reboot", pattern: "reboot | shutdown", scope: "All assets", action: "warn" },
  { id: "r4", name: "Allow read-only ops", pattern: "ls | cat | tail", scope: "All assets", action: "allow" },
  { id: "r5", name: "Block disk format", pattern: "mkfs.*", scope: "All Linux", action: "reject" },
]

function ActionBadge({ action }: { action: Action }) {
  const map: Record<Action, string> = {
    allow: "bg-success/10 text-success",
    reject: "bg-destructive/10 text-destructive",
    review: "bg-warning/10 text-warning",
    warn: "bg-accent text-accent-foreground",
  }
  return (
    <Badge variant="outline" className={`border-0 capitalize ${map[action]}`}>
      {action}
    </Badge>
  )
}

export default function CommandFilterPage() {
  return (
    <>
      <PageHeader
        title="Command Filter"
        breadcrumbs={[{ label: "Console" }, { label: "Access Control" }, { label: "Command Filter" }]}
        description="Block, warn, or require approval for sensitive commands during sessions."
        actions={
          <Button size="sm" className="gap-1.5">
            <Plus className="h-4 w-4" /> New Rule
          </Button>
        }
      />
      <Card className="overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Rule</TableHead>
              <TableHead>Pattern</TableHead>
              <TableHead>Scope</TableHead>
              <TableHead>Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rules.map((r) => (
              <TableRow key={r.id}>
                <TableCell className="font-medium">{r.name}</TableCell>
                <TableCell className="font-mono text-sm text-muted-foreground">{r.pattern}</TableCell>
                <TableCell className="text-sm">{r.scope}</TableCell>
                <TableCell>
                  <ActionBadge action={r.action} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </>
  )
}
