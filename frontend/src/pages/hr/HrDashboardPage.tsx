import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api, { apiError } from "../../api/client";
import type { DashboardStats, JobSummary } from "../../api/types";

/**
 * HR landing page: company-wide funnel stats plus the HR's own jobs, each
 * linking through to its pipeline. Two independent loads so a slow job list
 * doesn't block the stat cards.
 */
export default function HrDashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [jobs, setJobs] = useState<JobSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<DashboardStats>("/hr/dashboard")
      .then(({ data }) => setStats(data))
      .catch((err) => setError(apiError(err, "Could not load dashboard")));
    api
      .get<JobSummary[]>("/jobs/mine")
      .then(({ data }) => setJobs(data))
      .catch((err) => setError(apiError(err, "Could not load your jobs")));
  }, []);

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <Link
          to="/hr/jobs/new"
          className="rounded-md bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700"
        >
          Post a job
        </Link>
      </div>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}

      <div className="mb-8 grid grid-cols-2 gap-4 sm:grid-cols-5">
        <StatCard label="Active jobs" value={stats?.activeJobs} />
        <StatCard label="Applications" value={stats?.totalApplications} />
        <StatCard label="In assessment" value={stats?.inAssessment} />
        <StatCard label="In interview" value={stats?.inInterview} />
        <StatCard label="Ready for review" value={stats?.readyForReview} highlight />
      </div>

      <h2 className="mb-3 text-lg font-semibold">Your jobs</h2>
      {jobs === null && <p className="text-slate-500">Loading…</p>}
      {jobs?.length === 0 && (
        <p className="text-slate-500">No jobs yet. Post one to start hiring.</p>
      )}
      <ul className="space-y-3">
        {jobs?.map((job) => (
          <li key={job.id}>
            <Link
              to={`/hr/pipeline/${job.id}`}
              className="flex items-center justify-between rounded-lg border border-slate-200 bg-white p-4 hover:border-indigo-300 hover:shadow-sm"
            >
              <div>
                <span className="font-semibold text-slate-900">{job.title}</span>
                {job.location && <span className="ml-3 text-sm text-slate-500">{job.location}</span>}
              </div>
              <StatusPill status={job.status} />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

function StatCard({ label, value, highlight }: { label: string; value?: number; highlight?: boolean }) {
  return (
    <div
      className={`rounded-lg border p-4 ${
        highlight ? "border-green-200 bg-green-50" : "border-slate-200 bg-white"
      }`}
    >
      <div className="text-2xl font-bold text-slate-900">{value ?? "—"}</div>
      <div className="mt-1 text-xs text-slate-500">{label}</div>
    </div>
  );
}

function StatusPill({ status }: { status: string }) {
  const style =
    status === "ACTIVE"
      ? "bg-green-100 text-green-700"
      : status === "DRAFT"
        ? "bg-slate-100 text-slate-600"
        : "bg-amber-100 text-amber-700";
  return (
    <span className={`rounded-full px-3 py-1 text-xs font-medium ${style}`}>
      {status.toLowerCase()}
    </span>
  );
}
