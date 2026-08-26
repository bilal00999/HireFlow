import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { LogIn, Mail, Lock, Loader2, AlertCircle } from "lucide-react";

import { useAuth } from "../api/auth";
import { apiError } from "../api/client";
import { GlassCard } from "@/components/ui/glass-card";
import { GradientText } from "@/components/ui/gradient-text";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/**
 * Login for both roles — the backend returns the role, so we route candidates
 * to their applications and HR to the dashboard. Falls back to wherever the
 * user was headed before ProtectedRoute bounced them here. Behavior unchanged;
 * this is a visual reskin.
 */
export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const user = await login({ email, password });
      const from = (location.state as { from?: { pathname: string } } | null)?.from
        ?.pathname;
      navigate(from ?? (user.role === "HR" ? "/hr/dashboard" : "/jobs"), {
        replace: true,
      });
    } catch (err) {
      setError(apiError(err, "Invalid email or password"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <GlassCard
        hover={false}
        className="w-full max-w-md p-8"
        initial={{ opacity: 0, y: 20, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
      >
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 grid size-12 place-items-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-500 shadow-lg shadow-primary/30">
            <LogIn className="size-6 text-white" />
          </div>
          <h1 className="text-2xl font-bold">
            Welcome back to <GradientText>HireFlow</GradientText>
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Log in to continue
          </p>
        </div>

        {error && (
          <div className="mb-4 flex items-center gap-2 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
            <AlertCircle className="size-4 shrink-0" />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <div className="relative">
              <Mail className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="pl-9"
                placeholder="you@example.com"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <div className="relative">
              <Lock className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="pl-9"
                placeholder="••••••••"
              />
            </div>
          </div>

          <Button
            type="submit"
            variant="gradient"
            size="lg"
            disabled={busy}
            className="w-full"
          >
            {busy && <Loader2 className="size-4 animate-spin" />}
            {busy ? "Logging in…" : "Log in"}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          No account?{" "}
          <Link to="/register" className="font-medium text-primary hover:underline">
            Sign up
          </Link>
        </p>
      </GlassCard>
    </div>
  );
}
