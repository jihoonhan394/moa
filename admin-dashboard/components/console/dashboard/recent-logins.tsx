import { CheckCircle2, XCircle } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import { recentLogins } from "@/lib/mock-data"

export function RecentLogins() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Recent Logins</CardTitle>
      </CardHeader>
      <CardContent className="px-0">
        <ul className="divide-y divide-border">
          {recentLogins.map((l) => (
            <li key={l.id} className="flex items-center gap-3 px-6 py-2.5">
              <Avatar className="h-8 w-8">
                <AvatarFallback className="bg-secondary text-xs text-secondary-foreground">
                  {l.user.slice(0, 2).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{l.user}</div>
                <div className="font-mono text-xs text-muted-foreground">{l.ip}</div>
              </div>
              <span className="font-mono text-xs text-muted-foreground">{l.time}</span>
              <Badge
                variant="outline"
                className={cn(
                  "gap-1 border-0",
                  l.result === "success"
                    ? "bg-success/10 text-success"
                    : "bg-destructive/10 text-destructive",
                )}
              >
                {l.result === "success" ? <CheckCircle2 className="h-3 w-3" /> : <XCircle className="h-3 w-3" />}
                {l.result}
              </Badge>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
