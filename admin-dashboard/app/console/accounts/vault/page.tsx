import { RotateCw, Search } from "lucide-react"
import { PageHeader } from "@/components/console/page-header"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { VaultTable } from "@/components/console/accounts/vault-table"

export default function VaultPage() {
  return (
    <>
      <PageHeader
        title="Account Vault"
        breadcrumbs={[{ label: "Console" }, { label: "Accounts" }, { label: "Vault" }]}
        description="Privileged credentials stored for asset access. Reveals are audited."
        actions={
          <Button variant="outline" size="sm" className="gap-1.5">
            <RotateCw className="h-4 w-4" /> Bulk Rotate
          </Button>
        }
      />

      <Card className="overflow-hidden">
        <div className="border-b border-border p-3">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input placeholder="Search accounts..." className="h-9 pl-9" />
          </div>
        </div>
        <VaultTable />
      </Card>
    </>
  )
}
