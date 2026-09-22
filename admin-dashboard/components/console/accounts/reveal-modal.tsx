"use client"

import { useEffect, useState } from "react"
import { Eye, EyeOff, Copy, AlertTriangle, Check } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import type { VaultAccount } from "@/lib/mock-data"

export function RevealModal({
  account,
  open,
  onOpenChange,
}: {
  account: VaultAccount | null
  open: boolean
  onOpenChange: (v: boolean) => void
}) {
  const [shown, setShown] = useState(false)
  const [seconds, setSeconds] = useState(15)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!open) {
      setShown(false)
      setSeconds(15)
      setCopied(false)
    }
  }, [open])

  useEffect(() => {
    if (!shown) return
    if (seconds <= 0) {
      setShown(false)
      return
    }
    const t = setTimeout(() => setSeconds((s) => s - 1), 1000)
    return () => clearTimeout(t)
  }, [shown, seconds])

  const secret = "Pr0d-S3cr3t!92"

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Reveal Secret</DialogTitle>
        </DialogHeader>

        <div className="flex items-start gap-2 rounded-md bg-warning/10 p-3 text-sm text-warning">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>This action is strictly audited and logged.</span>
        </div>

        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">
            Account: <span className="font-mono text-foreground">{account?.account}</span> @{" "}
            <span className="font-mono text-foreground">{account?.asset}</span>
          </p>
          <div className="flex items-center gap-2">
            <Input
              readOnly
              value={shown ? secret : "••••••••••••"}
              className="font-mono"
            />
            <Button variant="outline" size="icon" onClick={() => { setShown((s) => !s); setSeconds(15) }} aria-label="Toggle visibility">
              {shown ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </Button>
            <Button
              variant="outline"
              size="icon"
              aria-label="Copy"
              onClick={() => {
                navigator.clipboard?.writeText(secret)
                setCopied(true)
                setTimeout(() => setCopied(false), 1500)
              }}
            >
              {copied ? <Check className="h-4 w-4 text-success" /> : <Copy className="h-4 w-4" />}
            </Button>
          </div>
          {shown && (
            <p className="font-mono text-xs text-muted-foreground">
              Auto-hide in: 00:{seconds.toString().padStart(2, "0")}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
