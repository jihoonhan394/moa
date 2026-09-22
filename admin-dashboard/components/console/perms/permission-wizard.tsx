"use client"

import { useState } from "react"
import { Check, ChevronRight, ChevronLeft, ArrowRight, ArrowLeft, Search } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"

const steps = ["Subject", "Assets", "Accounts", "Rules", "Review"]

const candidatePool = [
  "/Default/Prod/Web",
  "/Default/Prod/DB",
  "db-02",
  "web-04",
  "win-rdp-03",
  "cache-02",
]

export function PermissionWizard() {
  const [step, setStep] = useState(1) // 0-indexed active = "Assets"
  const [candidates, setCandidates] = useState(candidatePool)
  const [selected, setSelected] = useState<string[]>(["/Default/Prod/DB", "web-01"])

  const add = (item: string) => {
    setCandidates((c) => c.filter((x) => x !== item))
    setSelected((s) => [...s, item])
  }
  const remove = (item: string) => {
    setSelected((s) => s.filter((x) => x !== item))
    setCandidates((c) => [...c, item])
  }

  return (
    <Card className="overflow-hidden">
      {/* Stepper */}
      <div className="flex items-center gap-2 border-b border-border bg-muted/30 px-6 py-4">
        {steps.map((label, i) => {
          const done = i < step
          const active = i === step
          return (
            <div key={label} className="flex items-center gap-2">
              <div
                className={cn(
                  "flex h-7 w-7 items-center justify-center rounded-full text-xs font-semibold",
                  done && "bg-success text-success-foreground",
                  active && "bg-primary text-primary-foreground",
                  !done && !active && "bg-secondary text-muted-foreground",
                )}
              >
                {done ? <Check className="h-4 w-4" /> : i + 1}
              </div>
              <span className={cn("text-sm", active ? "font-medium text-foreground" : "text-muted-foreground")}>
                {label}
              </span>
              {i < steps.length - 1 && <ChevronRight className="h-4 w-4 text-muted-foreground" />}
            </div>
          )
        })}
      </div>

      {/* Body: Step 2 - Assets (transfer) */}
      <div className="p-6">
        <h2 className="text-base font-semibold">Select Assets or Nodes</h2>
        <div className="mt-3 flex flex-wrap items-center gap-4">
          <Label className="flex items-center gap-2 font-normal">
            <Checkbox defaultChecked /> Nodes
          </Label>
          <Label className="flex items-center gap-2 font-normal">
            <Checkbox defaultChecked /> Assets
          </Label>
          <Label className="flex items-center gap-2 font-normal">
            <Checkbox /> Labels
          </Label>
        </div>

        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
          {/* Candidates */}
          <div className="flex flex-col rounded-lg border border-border">
            <div className="border-b border-border p-3">
              <div className="mb-1 text-sm font-medium">Candidates</div>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input placeholder="Search..." className="h-8 pl-9" />
              </div>
            </div>
            <ul className="max-h-72 flex-1 overflow-y-auto p-2">
              {candidates.map((c) => (
                <li key={c} className="group flex items-center justify-between rounded-md px-2 py-1.5 text-sm hover:bg-secondary">
                  <span className="font-mono">{c}</span>
                  <Button variant="ghost" size="sm" className="h-7 gap-1 text-primary opacity-0 group-hover:opacity-100" onClick={() => add(c)}>
                    Add <ArrowRight className="h-3.5 w-3.5" />
                  </Button>
                </li>
              ))}
              {candidates.length === 0 && (
                <li className="px-2 py-6 text-center text-sm text-muted-foreground">All items selected</li>
              )}
            </ul>
          </div>

          {/* Selected */}
          <div className="flex flex-col rounded-lg border border-border">
            <div className="border-b border-border p-3">
              <div className="text-sm font-medium">Selected ({selected.length})</div>
            </div>
            <ul className="max-h-72 flex-1 overflow-y-auto p-2">
              {selected.map((s) => (
                <li key={s} className="group flex items-center justify-between rounded-md bg-accent/50 px-2 py-1.5 text-sm">
                  <span className="font-mono">{s}</span>
                  <Button variant="ghost" size="sm" className="h-7 gap-1 text-muted-foreground" onClick={() => remove(s)}>
                    <ArrowLeft className="h-3.5 w-3.5" /> Remove
                  </Button>
                </li>
              ))}
              {selected.length === 0 && (
                <li className="px-2 py-6 text-center text-sm text-muted-foreground">No items selected</li>
              )}
            </ul>
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between border-t border-border px-6 py-4">
        <Button variant="outline" className="gap-1.5" disabled={step === 0} onClick={() => setStep((s) => Math.max(0, s - 1))}>
          <ChevronLeft className="h-4 w-4" /> Previous
        </Button>
        <Button
          className="gap-1.5"
          onClick={() => {
            if (step < steps.length - 1) setStep((s) => s + 1)
            else toast.success("Permissions granted", { description: `${selected.length} assets assigned` })
          }}
        >
          {step < steps.length - 1 ? `Next: ${steps[step + 1]}` : "Grant Access"}
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </Card>
  )
}
