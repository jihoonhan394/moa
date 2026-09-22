import { PageHeader } from "@/components/console/page-header"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Separator } from "@/components/ui/separator"

const toggles = [
  { label: "Require MFA for all users", desc: "Enforce two-step verification on every login.", on: true },
  { label: "Record all SSH sessions", desc: "Store full terminal replays for auditing.", on: true },
  { label: "Auto-rotate credentials", desc: "Rotate privileged passwords every 90 days.", on: false },
  { label: "Block on policy violation", desc: "Immediately terminate sessions that violate command rules.", on: true },
]

export default function SettingsPage() {
  return (
    <>
      <PageHeader
        title="Settings"
        breadcrumbs={[{ label: "Console" }, { label: "Settings" }]}
        description="Organization-wide security policies."
      />
      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle className="text-base">Security Policies</CardTitle>
          <CardDescription>Control authentication and session behavior across the organization.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-1">
          {toggles.map((t, i) => (
            <div key={t.label}>
              {i > 0 && <Separator className="my-1" />}
              <div className="flex items-center justify-between py-3">
                <div className="pr-4">
                  <Label className="text-sm font-medium">{t.label}</Label>
                  <p className="text-sm text-muted-foreground">{t.desc}</p>
                </div>
                <Switch defaultChecked={t.on} />
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    </>
  )
}
