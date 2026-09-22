import { RefreshCw, Calendar } from "lucide-react"
import { PageHeader } from "@/components/console/page-header"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { SummaryCards } from "@/components/console/dashboard/summary-cards"
import { SessionTrendChart } from "@/components/console/dashboard/session-trend-chart"
import { ProtocolChart } from "@/components/console/dashboard/protocol-chart"
import { RecentLogins } from "@/components/console/dashboard/recent-logins"
import { ActiveSessions } from "@/components/console/dashboard/active-sessions"

export default function DashboardPage() {
  return (
    <>
      <PageHeader
        title="Dashboard"
        breadcrumbs={[{ label: "Console" }, { label: "Dashboard" }]}
        actions={
          <>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm" className="gap-2">
                  <Calendar className="h-4 w-4" />
                  Last 7 days
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem>Last 24 hours</DropdownMenuItem>
                <DropdownMenuItem>Last 7 days</DropdownMenuItem>
                <DropdownMenuItem>Last 30 days</DropdownMenuItem>
                <DropdownMenuItem>Last 90 days</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <Button variant="outline" size="sm" className="gap-2">
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
          </>
        }
      />

      <div className="space-y-4">
        <SummaryCards />

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <SessionTrendChart />
          <ProtocolChart />
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <RecentLogins />
          <ActiveSessions />
        </div>
      </div>
    </>
  )
}
