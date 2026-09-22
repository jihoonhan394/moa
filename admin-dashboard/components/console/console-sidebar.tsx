"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { ShieldHalf, ChevronLeft } from "lucide-react"
import { cn } from "@/lib/utils"
import { consoleNav } from "./nav-config"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"

export function ConsoleSidebar({
  collapsed,
  onToggle,
}: {
  collapsed: boolean
  onToggle: () => void
}) {
  const pathname = usePathname()

  return (
    <aside
      className={cn(
        "fixed inset-y-0 left-0 z-40 flex flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground transition-all duration-200",
        collapsed ? "w-16" : "w-60",
      )}
    >
      <div className="flex h-14 items-center gap-2 border-b border-sidebar-border px-4">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-primary text-primary-foreground">
          <ShieldHalf className="h-5 w-5" />
        </div>
        {!collapsed && <span className="text-sm font-semibold text-white">Sentinel</span>}
      </div>

      <nav className="flex-1 space-y-1 overflow-y-auto p-3">
        {consoleNav.map((item) => {
          const active = pathname === item.href || pathname.startsWith(item.href + "/")
          const Icon = item.icon
          return (
            <div key={item.label}>
              <Link
                href={item.href}
                title={collapsed ? item.label : undefined}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  active
                    ? "bg-sidebar-primary text-sidebar-primary-foreground"
                    : "text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
                )}
              >
                <Icon className="h-[18px] w-[18px] shrink-0" />
                {!collapsed && <span className="flex-1 truncate">{item.label}</span>}
                {!collapsed && item.badge && (
                  <Badge className="h-5 bg-sidebar-accent px-1.5 text-[11px] text-sidebar-accent-foreground">
                    {item.badge}
                  </Badge>
                )}
              </Link>
              {!collapsed && active && item.children && (
                <div className="ml-7 mt-1 space-y-1 border-l border-sidebar-border pl-3">
                  {item.children.map((child) => (
                    <Link
                      key={child.label}
                      href={child.href}
                      className={cn(
                        "block rounded-md px-2 py-1.5 text-[13px] transition-colors",
                        pathname === child.href
                          ? "text-white"
                          : "text-sidebar-foreground hover:text-white",
                      )}
                    >
                      {child.label}
                    </Link>
                  ))}
                </div>
              )}
            </div>
          )
        })}
      </nav>

      <div className="border-t border-sidebar-border p-3">
        <Button
          variant="ghost"
          size="sm"
          onClick={onToggle}
          className="w-full justify-start gap-3 text-sidebar-foreground hover:bg-sidebar-accent hover:text-white"
        >
          <ChevronLeft className={cn("h-[18px] w-[18px] transition-transform", collapsed && "rotate-180")} />
          {!collapsed && <span className="text-sm">Collapse</span>}
        </Button>
      </div>
    </aside>
  )
}
