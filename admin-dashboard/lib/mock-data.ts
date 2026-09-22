export type Platform = "Linux" | "Windows" | "MySQL" | "PostgreSQL" | "Redis" | "Network"
export type Protocol = "SSH" | "RDP" | "SFTP" | "DB" | "VNC"
export type AssetStatus = "online" | "offline"

export interface Asset {
  id: string
  name: string
  ip: string
  platform: Platform
  protocols: Protocol[]
  node: string
  accounts: number
  status: AssetStatus
  labels: string[]
  favorite?: boolean
}

export const assets: Asset[] = [
  { id: "a1", name: "web01", ip: "10.0.0.11", platform: "Linux", protocols: ["SSH", "SFTP"], node: "/Default/Prod/Web", accounts: 3, status: "online", labels: ["prod", "seoul"], favorite: true },
  { id: "a2", name: "web02", ip: "10.0.0.12", platform: "Linux", protocols: ["SSH"], node: "/Default/Prod/Web", accounts: 2, status: "online", labels: ["prod"] },
  { id: "a3", name: "web03", ip: "10.0.0.13", platform: "Linux", protocols: ["SSH", "SFTP"], node: "/Default/Prod/Web", accounts: 2, status: "offline", labels: ["prod", "seoul"] },
  { id: "a4", name: "db-prod-01", ip: "10.0.1.21", platform: "MySQL", protocols: ["DB"], node: "/Default/Prod/DB", accounts: 4, status: "online", labels: ["prod", "critical"], favorite: true },
  { id: "a5", name: "db-prod-02", ip: "10.0.1.22", platform: "PostgreSQL", protocols: ["DB"], node: "/Default/Prod/DB", accounts: 3, status: "online", labels: ["prod"] },
  { id: "a6", name: "win-rdp-01", ip: "10.0.2.31", platform: "Windows", protocols: ["RDP"], node: "/Default/Prod/Win", accounts: 2, status: "online", labels: ["prod", "windows"] },
  { id: "a7", name: "cache-01", ip: "10.0.3.41", platform: "Redis", protocols: ["DB"], node: "/Default/Stage/Cache", accounts: 1, status: "online", labels: ["stage"] },
  { id: "a8", name: "gw-edge-01", ip: "10.0.9.1", platform: "Network", protocols: ["SSH"], node: "/Default/Network", accounts: 1, status: "offline", labels: ["network"] },
  { id: "a9", name: "stage-web", ip: "10.0.4.11", platform: "Linux", protocols: ["SSH"], node: "/Default/Stage/Web", accounts: 2, status: "online", labels: ["stage"] },
  { id: "a10", name: "win-rdp-02", ip: "10.0.2.32", platform: "Windows", protocols: ["RDP", "VNC"], node: "/Default/Prod/Win", accounts: 2, status: "online", labels: ["prod", "windows"], favorite: true },
]

export interface TreeNode {
  id: string
  label: string
  count: number
  children?: TreeNode[]
}

export const nodeTree: TreeNode[] = [
  {
    id: "root",
    label: "Root",
    count: 340,
    children: [
      {
        id: "prod",
        label: "Production",
        count: 120,
        children: [
          { id: "prod-web", label: "Web", count: 40 },
          { id: "prod-db", label: "DB", count: 35 },
          { id: "prod-win", label: "Windows", count: 25 },
        ],
      },
      {
        id: "stage",
        label: "Staging",
        count: 80,
        children: [
          { id: "stage-web", label: "Web", count: 30 },
          { id: "stage-cache", label: "Cache", count: 12 },
        ],
      },
      { id: "network", label: "Network", count: 24 },
    ],
  },
]

export interface LoginRecord {
  id: string
  user: string
  ip: string
  time: string
  result: "success" | "failure"
}

export const recentLogins: LoginRecord[] = [
  { id: "l1", user: "user01", ip: "10.0.0.5", time: "14:32:11", result: "success" },
  { id: "l2", user: "admin", ip: "203.0.113.9", time: "14:28:54", result: "success" },
  { id: "l3", user: "j.kim", ip: "198.51.100.2", time: "14:21:03", result: "failure" },
  { id: "l4", user: "ops-bot", ip: "10.0.0.40", time: "14:15:47", result: "success" },
  { id: "l5", user: "s.lee", ip: "198.51.100.7", time: "14:09:22", result: "failure" },
  { id: "l6", user: "user01", ip: "10.0.0.5", time: "13:58:10", result: "success" },
]

export interface Session {
  id: string
  user: string
  asset: string
  account: string
  protocol: Protocol
  startTime: string
  duration: string
  clientIp: string
}

export const activeSessions: Session[] = [
  { id: "s1", user: "user01", asset: "web01", account: "root", protocol: "SSH", startTime: "14:02", duration: "12:31", clientIp: "10.0.0.5" },
  { id: "s2", user: "admin", asset: "db-prod-01", account: "dbadmin", protocol: "DB", startTime: "13:50", duration: "24:12", clientIp: "10.0.0.8" },
  { id: "s3", user: "j.park", asset: "win-rdp-01", account: "administrator", protocol: "RDP", startTime: "14:18", duration: "06:44", clientIp: "10.0.0.22" },
  { id: "s4", user: "ops-bot", asset: "web02", account: "deploy", protocol: "SSH", startTime: "14:25", duration: "01:09", clientIp: "10.0.0.40" },
]

export interface VaultAccount {
  id: string
  account: string
  asset: string
  type: "Password" | "SSH Key"
  lastRotated: string
  status: "ok" | "expiring" | "expired"
}

export const vaultAccounts: VaultAccount[] = [
  { id: "v1", account: "root", asset: "web01", type: "Password", lastRotated: "12 days ago", status: "ok" },
  { id: "v2", account: "dbadmin", asset: "db-prod-01", type: "Password", lastRotated: "84 days ago", status: "expiring" },
  { id: "v3", account: "deploy", asset: "web02", type: "SSH Key", lastRotated: "5 days ago", status: "ok" },
  { id: "v4", account: "administrator", asset: "win-rdp-01", type: "Password", lastRotated: "121 days ago", status: "expired" },
  { id: "v5", account: "svc-monitor", asset: "cache-01", type: "Password", lastRotated: "30 days ago", status: "ok" },
]

export const sessionTrend = [
  { time: "00:00", sessions: 8 },
  { time: "03:00", sessions: 5 },
  { time: "06:00", sessions: 11 },
  { time: "09:00", sessions: 28 },
  { time: "12:00", sessions: 34 },
  { time: "15:00", sessions: 41 },
  { time: "18:00", sessions: 26 },
  { time: "21:00", sessions: 17 },
]

export const protocolDistribution = [
  { name: "SSH", value: 184, fill: "hsl(var(--chart-1))" },
  { name: "RDP", value: 76, fill: "hsl(var(--chart-3))" },
  { name: "DB", value: 58, fill: "hsl(var(--chart-4))" },
  { name: "VNC", value: 22, fill: "hsl(var(--chart-5))" },
]

export interface CommandEntry {
  ts: string
  command: string
  danger?: boolean
}

export const commandHistory: CommandEntry[] = [
  { ts: "00:12", command: "cd /var/www" },
  { ts: "00:31", command: "ls -al" },
  { ts: "01:05", command: "tail -f app.log" },
  { ts: "01:48", command: "systemctl status nginx" },
  { ts: "02:03", command: "rm -rf tmp/", danger: true },
  { ts: "03:22", command: "git pull origin main" },
  { ts: "04:10", command: "npm run build" },
  { ts: "05:02", command: "sudo reboot", danger: true },
]
