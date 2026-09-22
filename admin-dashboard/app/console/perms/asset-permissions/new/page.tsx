import { PageHeader } from "@/components/console/page-header"
import { PermissionWizard } from "@/components/console/perms/permission-wizard"

export default function GrantPermissionsPage() {
  return (
    <>
      <PageHeader
        title="Grant Asset Permissions"
        breadcrumbs={[{ label: "Console" }, { label: "Permissions" }, { label: "Grant Access" }]}
        description="Assign fine-grained asset access to users or groups."
      />
      <PermissionWizard />
    </>
  )
}
