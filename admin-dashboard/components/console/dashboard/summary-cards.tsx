import { Server, Users, Radio, Ticket, TrendingUp, TrendingDown } from "lucide-react"
import { Card } from "@/components/ui/card"
import { cn } from "@/lib/utils"

interface Metric {
  label: string
  value: string
  icon: typeof Server
  trend?: { value: string; up: boolean }
  dot?: "green" | "orange"
}

const metrics: Metric[] = [
  { label: "Total Assets", value: "340", icon: Server, trend: { value: "+12% from last week", up: true } },
  { label: "Total Users", value: "128", icon: Users, trend: { value: "+4 this month", up: true } },
  { label: "Online Sessions", value: "17", icon: Radio, dot: "green", trend: { value: "-3 vs yesterday", up: false } },
  { label: "Pending Tickets", value: "5", icon: Ticket, dot: "orange", trend: { value: "2 awaiting approval", up: true } },
]

export function SummaryCards() {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {metrics.map((m) => {
        const Icon = m.icon
        return (
          <Card key={m.label} className="p-5">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                {m.dot && (
                  <span className={cn("h-2 w-2 rounded-full", m.dot === "green" ? "bg-success" : "bg-warning")} />
                )}
                {m.label}
              </div>
              <div className="flex h-9 w-9 items-center justify-center rounded-md bg-accent text-accent-foreground">
                <Icon className="h-[18px] w-[18px]" />
              </div>
            </div>
            <div className="mt-3 text-3xl font-semibold tracking-tight">{m.value}</div>
            {m.trend && (
              <div
                className={cn(
                  "mt-2 flex items-center gap-1 text-xs",
                  m.trend.up ? "text-success" : "text-muted-foreground",
                )}
              >
                {m.trend.up ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
                {m.trend.value}
              </div>
            )}
          </Card>
        )
      })}
    </div>
  )
}
