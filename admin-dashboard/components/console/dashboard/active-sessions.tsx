import Link from "next/link"
import { Eye } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { ProtocolBadge } from "@/components/console/status"
import { activeSessions } from "@/lib/mock-data"

export function ActiveSessions() {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-base">Active Sessions</CardTitle>
        <Link href="/console/audit/sessions/online" className="text-sm text-primary hover:underline">
          View all
        </Link>
      </CardHeader>
      <CardContent className="px-0">
        <ul className="divide-y divide-border">
          {activeSessions.map((s) => (
            <li key={s.id} className="flex items-center gap-3 px-6 py-2.5">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate text-sm font-medium">{s.asset}</span>
                  <ProtocolBadge protocol={s.protocol} />
                </div>
                <div className="text-xs text-muted-foreground">
                  {s.user} · {s.account} · <span className="font-mono">{s.duration}</span>
                </div>
              </div>
              <Button variant="outline" size="sm" className="gap-1.5" asChild>
                <Link href="/console/audit/sessions/online">
                  <Eye className="h-3.5 w-3.5" />
                  Monitor
                </Link>
              </Button>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
