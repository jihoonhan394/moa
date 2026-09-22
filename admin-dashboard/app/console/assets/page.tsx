import { Plus, ChevronDown } from "lucide-react"
import { PageHeader } from "@/components/console/page-header"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { NodeTree } from "@/components/console/assets/node-tree"
import { AssetsTable } from "@/components/console/assets/assets-table"
import { CreateAssetDrawer } from "@/components/console/assets/create-asset-drawer"

export default function AssetsPage() {
  return (
    <>
      <PageHeader
        title="Assets"
        breadcrumbs={[{ label: "Console" }, { label: "Assets" }]}
        actions={
          <>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm" className="gap-1.5">
                  Import/Export
                  <ChevronDown className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem>Import from CSV</DropdownMenuItem>
                <DropdownMenuItem>Export to CSV</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <CreateAssetDrawer>
              <Button size="sm" className="gap-1.5">
                <Plus className="h-4 w-4" /> Create Asset
              </Button>
            </CreateAssetDrawer>
          </>
        }
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[260px_1fr]">
        <Card className="h-fit p-3">
          <NodeTree />
        </Card>
        <Card className="overflow-hidden">
          <AssetsTable />
        </Card>
      </div>
    </>
  )
}
