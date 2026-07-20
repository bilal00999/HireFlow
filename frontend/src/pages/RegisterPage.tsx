import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../api/auth";
import { apiError } from "../api/client";
import type { Role } from "../api/types";

/**
 * Registration for both roles. A toggle switches between the candidate and
 * company forms, which post to different endpoints and collect different fields.
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
        await registerCompany({ email, password, companyName, industry: industry || undefined });
        navigate("/hr/dashboard", { replace: true });
      }
    } catch (err) {
      setError(apiError(err, "Registration failed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="mb-6 text-2xl font-bold">Create your account</h1>

      <div className="mb-6 grid grid-cols-2 gap-2 rounded-md bg-slate-100 p-1">
        <RoleTab active={role === "CANDIDATE"} onClick={() => setRole("CANDIDATE")}>
          Candidate
        </RoleTab>
        <RoleTab active={role === "HR"} onClick={() => setRole("HR")}>
          Company / HR
        </RoleTab>
      </div>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <Field label="Email">
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2" />
        </Field>
        <Field label="Password">
          <input type="password" required minLength={6} value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2" />
        </Field>

        {role === "CANDIDATE" ? (
          <>
            <Field label="Full name">
              <input required value={fullName} onChange={(e) => setFullName(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2" />
            </Field>
            <Field label="Phone (optional)">
              <input value={phone} onChange={(e) => setPhone(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2" />
            </Field>
          </>
        ) : (
          <>
            <Field label="Company name">
              <input required value={companyName} onChange={(e) => setCompanyName(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2" />
            </Field>
            <Field label="Industry (optional)">
              <input value={industry} onChange={(e) => setIndustry(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2" />
            </Field>
          </>
        )}

        <button type="submit" disabled={busy}
          className="w-full rounded-md bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-60">
          {busy ? "Creating account…" : "Create account"}
        </button>
      </form>

      <p className="mt-4 text-sm text-slate-600">
        Already have an account?{" "}
        <Link to="/login" className="font-medium text-indigo-600 hover:underline">
          Log in
        </Link>
      </p>
    </div>
  );
}

function RoleTab({ active, onClick, children }: {
  active: boolean; onClick: () => void; children: React.ReactNode;
}) {
  return (
    <button type="button" onClick={onClick}
      className={`rounded px-3 py-1.5 text-sm font-medium ${
        active ? "bg-white text-indigo-600 shadow-sm" : "text-slate-600"
      }`}>
      {children}
    </button>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      {children}
    </label>
  );
}
