import { cn } from "@/lib/utils"
import { Server, Database, MonitorSmartphone, Network as NetworkIcon, HardDrive } from "lucide-react"
import type { Platform, Protocol } from "@/lib/mock-data"

export function StatusDot({ status, label }: { status: "online" | "offline"; label?: boolean }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className={cn(
          "h-2 w-2 rounded-full",
          status === "online" ? "bg-success" : "bg-muted-foreground/40",
        )}
      />
      {label && (
        <span className={cn("text-sm capitalize", status === "online" ? "text-foreground" : "text-muted-foreground")}>
          {status}
        </span>
      )}
    </span>
  )
}

const platformIconMap: Record<Platform, typeof Server> = {
  Linux: Server,
  Windows: MonitorSmartphone,
  MySQL: Database,
  PostgreSQL: Database,
  Redis: HardDrive,
  Network: NetworkIcon,
}

export function PlatformIcon({ platform, className }: { platform: Platform; className?: string }) {
  const Icon = platformIconMap[platform] ?? Server
  return <Icon className={cn("h-4 w-4 text-muted-foreground", className)} />
}

const protocolColor: Record<Protocol, string> = {
  SSH: "bg-accent text-accent-foreground",
  RDP: "bg-secondary text-secondary-foreground",
  SFTP: "bg-secondary text-secondary-foreground",
  DB: "bg-secondary text-secondary-foreground",
  VNC: "bg-secondary text-secondary-foreground",
}

export function ProtocolBadge({ protocol }: { protocol: Protocol }) {
  return (
    <span className={cn("inline-flex items-center rounded px-1.5 py-0.5 font-mono text-[11px] font-medium", protocolColor[protocol])}>
      {protocol}
    </span>
  )
}
