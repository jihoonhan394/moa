"use client"

import { useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { Search, Star, Terminal, Database, MonitorPlay } from "lucide-react"
import { assets, type Asset, type Protocol } from "@/lib/mock-data"
import { PlatformIcon, ProtocolBadge, StatusDot } from "@/components/console/status"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { cn } from "@/lib/utils"

function connectIcon(protocol: Protocol) {
  if (protocol === "DB") return Database
  if (protocol === "RDP" || protocol === "VNC") return MonitorPlay
  return Terminal
}

export function AssetGrid() {
  const router = useRouter()
  const [query, setQuery] = useState("")
  const [filter, setFilter] = useState<"all" | "favorites" | "online">("all")

  const filtered = useMemo(() => {
    return assets.filter((a) => {
      const matchesQuery =
        a.name.toLowerCase().includes(query.toLowerCase()) ||
        a.ip.includes(query) ||
        a.labels.some((l) => l.includes(query.toLowerCase()))
      const matchesFilter =
        filter === "all" || (filter === "favorites" && a.favorite) || (filter === "online" && a.status === "online")
      return matchesQuery && matchesFilter
    })
  }, [query, filter])

  function connect(asset: Asset, protocol: Protocol) {
    router.push(`/connect/${protocol === "DB" ? "db" : protocol === "RDP" || protocol === "VNC" ? "rdp" : "ssh"}?asset=${asset.name}`)
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search by name, IP, or label..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="bg-background pl-9"
          />
        </div>
        <Tabs value={filter} onValueChange={(v) => setFilter(v as typeof filter)}>
          <TabsList>
            <TabsTrigger value="all">All</TabsTrigger>
            <TabsTrigger value="favorites">Favorites</TabsTrigger>
            <TabsTrigger value="online">Online</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {filtered.length === 0 ? (
        <div className="rounded-xl border border-dashed py-16 text-center text-sm text-muted-foreground">
          No assets match your search.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((asset) => {
            const primary = asset.protocols[0]
            const PrimaryIcon = connectIcon(primary)
            return (
              <div
                key={asset.id}
                className="group flex flex-col gap-4 rounded-xl border bg-card p-4 shadow-sm transition-colors hover:border-primary/40"
              >
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div className="flex size-9 items-center justify-center rounded-lg bg-muted">
                      <PlatformIcon platform={asset.platform} className="size-5 text-foreground" />
                    </div>
                    <div>
                      <div className="flex items-center gap-1.5">
                        <span className="font-medium leading-none">{asset.name}</span>
                        {asset.favorite && <Star className="size-3.5 fill-warning text-warning" />}
                      </div>
                      <span className="font-mono text-xs text-muted-foreground">{asset.ip}</span>
                    </div>
                  </div>
                  <StatusDot status={asset.status} />
                </div>

                <div className="flex flex-wrap items-center gap-1.5">
                  {asset.protocols.map((p) => (
                    <ProtocolBadge key={p} protocol={p} />
                  ))}
                  {asset.labels.map((l) => (
                    <Badge key={l} variant="outline" className="text-[11px] font-normal">
                      {l}
                    </Badge>
                  ))}
                </div>

                <div className="mt-auto flex items-center gap-2">
                  <Button
                    size="sm"
                    className="flex-1 gap-1.5"
                    disabled={asset.status === "offline"}
                    onClick={() => connect(asset, primary)}
                  >
                    <PrimaryIcon className="size-4" />
                    Connect
                  </Button>
                  {asset.protocols.length > 1 && (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button size="sm" variant="outline" disabled={asset.status === "offline"}>
                          {asset.protocols.length} protocols
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {asset.protocols.map((p) => {
                          const Icon = connectIcon(p)
                          return (
                            <DropdownMenuItem key={p} onClick={() => connect(asset, p)}>
                              <Icon className="mr-2 size-4" />
                              Connect via {p}
                            </DropdownMenuItem>
                          )
                        })}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
