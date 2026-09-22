"use client"

import { useState } from "react"
import { cn } from "@/lib/utils"
import { ConsoleSidebar } from "./console-sidebar"
import { ConsoleTopbar } from "./console-topbar"

export function ConsoleShell({ children }: { children: React.ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <div className="min-h-screen bg-background">
      <ConsoleSidebar collapsed={collapsed} onToggle={() => setCollapsed((c) => !c)} />
      <div className={cn("flex min-h-screen flex-col transition-all duration-200", collapsed ? "pl-16" : "pl-60")}>
        <ConsoleTopbar />
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  )
}
