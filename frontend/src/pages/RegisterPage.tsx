import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  UserPlus,
  Mail,
  Lock,
  User,
  Phone,
  Building2,
  Briefcase,
  Loader2,
  AlertCircle,
} from "lucide-react";

import { useAuth } from "../api/auth";
import { apiError } from "../api/client";
import type { Role } from "../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { GradientText } from "@/components/ui/gradient-text";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

/**
 * Registration for both roles. Tabs switch between the candidate and company
 * forms, which post to different endpoints and collect different fields.
 * Behavior unchanged; visual reskin only.
 */
export default function RegisterPage() {
  const { registerCandidate, registerCompany } = useAuth();
  const navigate = useNavigate();

  const [role, setRole] = useState<Role>("CANDIDATE");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [industry, setIndustry] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (role === "CANDIDATE") {
        await registerCandidate({ email, password, fullName, phone: phone || undefined });
        navigate("/jobs", { replace: true });
      } else {
        await registerCompany({
          email,
          password,
          companyName,
          industry: industry || undefined,
        });
        navigate("/hr/dashboard", { replace: true });
      }
    } catch (err) {
      setError(apiError(err, "Registration failed"));
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
        <div className="mb-6 text-center">
          <div className="mx-auto mb-4 grid size-12 place-items-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-500 shadow-lg shadow-primary/30">
            <UserPlus className="size-6 text-white" />
          </div>
          <h1 className="text-2xl font-bold">
            Join <GradientText>HireFlow</GradientText>
          </h1>
        </div>

        <Tabs
          value={role}
          onValueChange={(v) => setRole(v as Role)}
          className="mb-5"
        >
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="CANDIDATE">Candidate</TabsTrigger>
            <TabsTrigger value="HR">Company / HR</TabsTrigger>
          </TabsList>
        </Tabs>

        {error && (
          <div className="mb-4 flex items-center gap-2 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
            <AlertCircle className="size-4 shrink-0" />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <IconField id="email" label="Email" icon={Mail}>
            <Input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="pl-9"
              placeholder="you@example.com"
            />
          </IconField>

          <IconField id="password" label="Password" icon={Lock}>
            <Input
              id="password"
              type="password"
              required
              minLength={6}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="pl-9"
              placeholder="At least 6 characters"
            />
          </IconField>

          {role === "CANDIDATE" ? (
            <>
              <IconField id="fullName" label="Full name" icon={User}>
                <Input
                  id="fullName"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="pl-9"
                />
              </IconField>
              <IconField id="phone" label="Phone (optional)" icon={Phone}>
                <Input
                  id="phone"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="pl-9"
                />
              </IconField>
            </>
          ) : (
            <>
              <IconField id="companyName" label="Company name" icon={Building2}>
                <Input
                  id="companyName"
                  required
                  value={companyName}
                  onChange={(e) => setCompanyName(e.target.value)}
                  className="pl-9"
                />
              </IconField>
              <IconField id="industry" label="Industry (optional)" icon={Briefcase}>
                <Input
                  id="industry"
                  value={industry}
                  onChange={(e) => setIndustry(e.target.value)}
                  className="pl-9"
                />
              </IconField>
            </>
          )}

          <Button
            type="submit"
            variant="gradient"
            size="lg"
            disabled={busy}
            className="w-full"
          >
            {busy && <Loader2 className="size-4 animate-spin" />}
            {busy ? "Creating account…" : "Create account"}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Log in
          </Link>
        </p>
      </GlassCard>
    </div>
  );
}

function IconField({
  id,
  label,
  icon: Icon,
  children,
}: {
  id: string;
  label: string;
  icon: typeof Mail;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Icon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        {children}
      </div>
    </div>
  );
}
