import { Search } from "lucide-react"
import { PageHeader } from "@/components/console/page-header"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { OnlineSessionsTable } from "@/components/console/audit/online-sessions-table"

export default function OnlineSessionsPage() {
  return (
    <>
      <PageHeader
        title="Online Sessions"
        breadcrumbs={[{ label: "Console" }, { label: "Audit" }, { label: "Online Sessions" }]}
        actions={
          <span className="flex items-center gap-2 rounded-md bg-success/10 px-3 py-1.5 text-sm font-medium text-success">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-75" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-success" />
            </span>
            Live (WebSocket Connected)
          </span>
        }
      />

      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-2 border-b border-border p-3">
          <div className="relative min-w-[200px] flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input placeholder="Search sessions..." className="h-9 pl-9" />
          </div>
          <Select defaultValue="all">
            <SelectTrigger className="h-9 w-[130px]">
              <SelectValue placeholder="Protocol" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Protocols</SelectItem>
              <SelectItem value="ssh">SSH</SelectItem>
              <SelectItem value="rdp">RDP</SelectItem>
              <SelectItem value="db">DB</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <OnlineSessionsTable />
      </Card>
    </>
  )
}
