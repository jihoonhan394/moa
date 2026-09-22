"use client"

import { Pie, PieChart, Cell } from "recharts"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { protocolDistribution } from "@/lib/mock-data"

const chartConfig = {
  value: { label: "Assets" },
  SSH: { label: "SSH", color: "hsl(var(--chart-1))" },
  RDP: { label: "RDP", color: "hsl(var(--chart-3))" },
  DB: { label: "DB", color: "hsl(var(--chart-4))" },
  VNC: { label: "VNC", color: "hsl(var(--chart-5))" },
} satisfies ChartConfig

const total = protocolDistribution.reduce((s, d) => s + d.value, 0)

export function ProtocolChart() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Protocol Distribution</CardTitle>
      </CardHeader>
      <CardContent>
        <ChartContainer config={chartConfig} className="mx-auto aspect-square h-[200px]">
          <PieChart>
            <ChartTooltip content={<ChartTooltipContent nameKey="name" />} />
            <Pie data={protocolDistribution} dataKey="value" nameKey="name" innerRadius={55} outerRadius={85} strokeWidth={2}>
              {protocolDistribution.map((entry) => (
                <Cell key={entry.name} fill={entry.fill} />
              ))}
            </Pie>
          </PieChart>
        </ChartContainer>
        <div className="mt-2 space-y-2">
          {protocolDistribution.map((d) => (
            <div key={d.name} className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-2">
                <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: d.fill }} />
                {d.name}
              </span>
              <span className="text-muted-foreground">
                {d.value} <span className="text-xs">({Math.round((d.value / total) * 100)}%)</span>
              </span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
