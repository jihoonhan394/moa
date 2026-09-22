"use client"

import { useState } from "react"
import { Play, Pause, Bookmark, Search, AlertTriangle, ChevronDown } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Slider } from "@/components/ui/slider"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { commandHistory } from "@/lib/mock-data"
import { cn } from "@/lib/utils"

export function ReplayPlayer() {
  const [playing, setPlaying] = useState(false)
  const [speed, setSpeed] = useState("1x")

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_320px]">
      {/* Player */}
      <div className="flex flex-col overflow-hidden rounded-lg border border-border bg-card">
        <div className="flex-1 bg-black p-4 font-mono text-sm leading-relaxed text-green-400">
          <p className="text-muted-foreground">Last login: Mon Jun 16 14:02:11 from 10.0.0.5</p>
          <p>[root@web01 ~]# cd /var/www</p>
          <p>[root@web01 www]# ls -al</p>
          <p className="text-foreground/70">total 24</p>
          <p className="text-foreground/70">drwxr-xr-x 4 root root 4096 Jun 16 13:58 .</p>
          <p>[root@web01 www]# tail -f app.log</p>
          <p className="text-foreground/70">[INFO] server listening on :8080</p>
          <p>
            [root@web01 www]# <span className="text-red-400">rm -rf tmp/</span>
          </p>
          <p>
            [root@web01 www]# <span className="inline-block h-4 w-2 animate-pulse bg-green-400 align-middle" />
          </p>
        </div>

        {/* Controls */}
        <div className="flex items-center gap-3 border-t border-border bg-card px-4 py-3">
          <Button size="icon" className="h-9 w-9 shrink-0" onClick={() => setPlaying((p) => !p)}>
            {playing ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
          </Button>
          <span className="font-mono text-xs text-muted-foreground tabular-nums">04:21 / 14:31</span>
          <Slider defaultValue={[30]} max={100} step={1} className="flex-1" />
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" className="h-8 gap-1 font-mono">
                {speed}
                <ChevronDown className="h-3.5 w-3.5" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {["0.5x", "1x", "1.5x", "2x", "4x"].map((s) => (
                <DropdownMenuItem key={s} onClick={() => setSpeed(s)}>
                  {s}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
          <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Bookmark">
            <Bookmark className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Command history */}
      <div className="flex flex-col overflow-hidden rounded-lg border border-border bg-card">
        <div className="border-b border-border p-3">
          <h3 className="mb-2 text-sm font-semibold">Command History</h3>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input placeholder="Filter commands..." className="h-9 pl-9" />
          </div>
        </div>
        <ul className="flex-1 overflow-y-auto">
          {commandHistory.map((c, i) => (
            <li
              key={i}
              className={cn(
                "flex cursor-pointer items-center gap-3 px-3 py-2 text-sm transition-colors hover:bg-secondary",
                c.danger && "bg-destructive/10 hover:bg-destructive/15",
              )}
            >
              <span className="font-mono text-xs text-muted-foreground tabular-nums">{c.ts}</span>
              <span className={cn("flex-1 font-mono", c.danger && "text-destructive")}>{c.command}</span>
              {c.danger && <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-destructive" />}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
