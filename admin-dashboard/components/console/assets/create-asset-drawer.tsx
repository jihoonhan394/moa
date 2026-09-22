"use client"

import { useState } from "react"
import { Plus, Pencil, Trash2, Plug } from "lucide-react"
import { toast } from "sonner"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Separator } from "@/components/ui/separator"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion"
import { Badge } from "@/components/ui/badge"

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h3 className="mb-3 text-sm font-semibold text-foreground">{children}</h3>
}

export function CreateAssetDrawer({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false)

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>{children}</SheetTrigger>
      <SheetContent className="flex w-full flex-col gap-0 p-0 sm:max-w-[640px]">
        <SheetHeader className="border-b border-border px-6 py-4">
          <SheetTitle>Create Asset</SheetTitle>
        </SheetHeader>

        <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
          {/* Basic Info */}
          <section>
            <SectionTitle>Basic Info</SectionTitle>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label>Platform</Label>
                <Select defaultValue="Linux">
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Linux">Linux</SelectItem>
                    <SelectItem value="Windows">Windows</SelectItem>
                    <SelectItem value="MySQL">MySQL</SelectItem>
                    <SelectItem value="PostgreSQL">PostgreSQL</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label>Name</Label>
                <Input defaultValue="web03" />
              </div>
              <div className="space-y-1.5">
                <Label>Node</Label>
                <Select defaultValue="web">
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="web">/Default/Prod/Web</SelectItem>
                    <SelectItem value="db">/Default/Prod/DB</SelectItem>
                    <SelectItem value="stage">/Default/Stage/Web</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label>IP Address</Label>
                <Input defaultValue="10.0.0.13" className="font-mono" />
              </div>
              <div className="col-span-2 space-y-1.5">
                <Label>Labels</Label>
                <div className="flex flex-wrap items-center gap-1.5 rounded-md border border-input px-3 py-2">
                  <Badge variant="secondary">prod</Badge>
                  <Badge variant="secondary">seoul</Badge>
                  <input
                    className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
                    placeholder="Add label..."
                  />
                </div>
              </div>
            </div>
          </section>

          <Separator />

          {/* Protocols */}
          <section>
            <SectionTitle>Protocols</SectionTitle>
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <Checkbox id="ssh" defaultChecked />
                <Label htmlFor="ssh" className="w-16">SSH</Label>
                <span className="text-sm text-muted-foreground">Port</span>
                <Input defaultValue="22" className="h-8 w-20 font-mono" />
              </div>
              <div className="flex items-center gap-3">
                <Checkbox id="sftp" />
                <Label htmlFor="sftp" className="w-16">SFTP</Label>
                <span className="text-sm text-muted-foreground">Port</span>
                <Input defaultValue="22" className="h-8 w-20 font-mono" />
              </div>
              <Button variant="ghost" size="sm" className="gap-1.5 text-primary">
                <Plus className="h-4 w-4" /> Add Protocol
              </Button>
              <div className="space-y-1.5 pt-1">
                <Label>Gateway</Label>
                <Select defaultValue="direct">
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="direct">Direct Connect</SelectItem>
                    <SelectItem value="gw1">gw-seoul-01</SelectItem>
                    <SelectItem value="gw2">gw-tokyo-01</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </section>

          <Separator />

          {/* Accounts */}
          <section>
            <SectionTitle>Accounts</SectionTitle>
            <div className="overflow-hidden rounded-md border border-border">
              <table className="w-full text-sm">
                <thead className="bg-muted/50 text-xs text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 text-left font-medium">Username</th>
                    <th className="px-3 py-2 text-left font-medium">Type</th>
                    <th className="px-3 py-2 text-left font-medium">Secret</th>
                    <th className="px-3 py-2 text-left font-medium">Privileged</th>
                    <th className="px-3 py-2 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="border-t border-border">
                    <td className="px-3 py-2 font-mono">root</td>
                    <td className="px-3 py-2">Password</td>
                    <td className="px-3 py-2 font-mono text-muted-foreground">******</td>
                    <td className="px-3 py-2">Yes</td>
                    <td className="px-3 py-2">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" className="h-7 w-7">
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                        <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive">
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <Button variant="outline" size="sm" className="mt-3 gap-1.5">
              <Plus className="h-4 w-4" /> Add Account
            </Button>
          </section>

          <Separator />

          {/* Advanced */}
          <Accordion type="single" collapsible>
            <AccordionItem value="advanced" className="border-0">
              <AccordionTrigger className="py-0 text-sm font-semibold hover:no-underline">
                Advanced
              </AccordionTrigger>
              <AccordionContent className="pt-4">
                <div className="flex items-center gap-3">
                  <Checkbox id="auto-test" defaultChecked />
                  <Label htmlFor="auto-test">Run connectivity test on save</Label>
                </div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        </div>

        <div className="flex items-center justify-between border-t border-border px-6 py-4">
          <Button
            variant="outline"
            className="gap-1.5"
            onClick={() => toast.success("Connectivity test passed", { description: "10.0.0.13:22 reachable" })}
          >
            <Plug className="h-4 w-4" /> Test Connectivity
          </Button>
          <div className="flex gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => {
                toast.success("Asset created", { description: "web03 added to /Default/Prod/Web" })
                setOpen(false)
              }}
            >
              Save
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  )
}
