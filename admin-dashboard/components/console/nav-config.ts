import {
  LayoutDashboard,
  Server,
  KeyRound,
  ShieldCheck,
  Activity,
  Lock,
  Settings,
  type LucideIcon,
} from "lucide-react"

export interface NavItem {
  label: string
  href: string
  icon: LucideIcon
  badge?: string
  children?: { label: string; href: string }[]
}

export const consoleNav: NavItem[] = [
  { label: "Dashboard", href: "/console/dashboard", icon: LayoutDashboard },
  {
    label: "Assets",
    href: "/console/assets",
    icon: Server,
    children: [
      { label: "Asset List", href: "/console/assets" },
      { label: "Nodes", href: "/console/assets" },
    ],
  },
  {
    label: "Accounts",
    href: "/console/accounts/vault",
    icon: KeyRound,
    children: [{ label: "Vault", href: "/console/accounts/vault" }],
  },
  {
    label: "Permissions",
    href: "/console/perms/asset-permissions/new",
    icon: ShieldCheck,
    children: [{ label: "Grant Access", href: "/console/perms/asset-permissions/new" }],
  },
  {
    label: "Access Control",
    href: "/console/acl/command",
    icon: Lock,
  },
  {
    label: "Audit",
    href: "/console/audit/sessions/online",
    icon: Activity,
    badge: "17",
    children: [
      { label: "Online Sessions", href: "/console/audit/sessions/online" },
      { label: "Session Replay", href: "/console/audit/sessions/replay" },
    ],
  },
  { label: "Settings", href: "/console/settings", icon: Settings },
]
