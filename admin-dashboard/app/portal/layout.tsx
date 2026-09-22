import type React from "react"
import { PortalHeader } from "@/components/portal/portal-header"

export default function PortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-svh bg-muted/30">
      <PortalHeader />
      <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
    </div>
  )
}
