"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { Shield, ArrowLeft } from "lucide-react"
import { Button } from "@/components/ui/button"
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp"

export default function MfaPage() {
  const router = useRouter()
  const [code, setCode] = useState("")
  const [loading, setLoading] = useState(false)

  function verify(value: string) {
    setLoading(true)
    setTimeout(() => router.push("/portal/assets"), 700)
  }

  return (
    <div className="flex min-h-svh items-center justify-center bg-muted/40 p-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <div className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <Shield className="size-6" />
          </div>
          <div>
            <h1 className="text-xl font-semibold tracking-tight">Two-Factor Authentication</h1>
            <p className="text-sm text-muted-foreground text-balance">
              Enter the 6-digit code from your authenticator app
            </p>
          </div>
        </div>

        <div className="rounded-xl border bg-card p-6 shadow-sm">
          <div className="flex flex-col items-center gap-6">
            <InputOTP
              maxLength={6}
              value={code}
              onChange={(v) => {
                setCode(v)
                if (v.length === 6) verify(v)
              }}
            >
              <InputOTPGroup>
                <InputOTPSlot index={0} />
                <InputOTPSlot index={1} />
                <InputOTPSlot index={2} />
                <InputOTPSlot index={3} />
                <InputOTPSlot index={4} />
                <InputOTPSlot index={5} />
              </InputOTPGroup>
            </InputOTP>

            <Button className="w-full" disabled={code.length < 6 || loading} onClick={() => verify(code)}>
              {loading ? "Verifying..." : "Verify"}
            </Button>

            <button className="text-sm text-primary hover:underline" onClick={() => setCode("")}>
              Resend code
            </button>
          </div>
        </div>

        <Link
          href="/login"
          className="mt-6 flex items-center justify-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="size-4" />
          Back to login
        </Link>
      </div>
    </div>
  )
}
