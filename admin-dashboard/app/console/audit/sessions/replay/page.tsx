import { Download } from "lucide-react"
import { PageHeader } from "@/components/console/page-header"
import { Button } from "@/components/ui/button"
import { ReplayPlayer } from "@/components/console/audit/replay-player"

export default function ReplayPage() {
  return (
    <>
      <PageHeader
        title="Session Replay"
        breadcrumbs={[
          { label: "Console" },
          { label: "Audit" },
          { label: "Sessions", href: "/console/audit/sessions/online" },
          { label: "Replay" },
        ]}
        description="web01 · root · SSH · user01 · 2026-06-16 14:02"
        actions={
          <Button variant="outline" size="sm" className="gap-1.5">
            <Download className="h-4 w-4" /> Download
          </Button>
        }
      />
      <ReplayPlayer />
    </>
  )
}
