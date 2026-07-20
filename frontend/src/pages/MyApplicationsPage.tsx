import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api, { apiError } from "../api/client";
import type { MyApplication } from "../api/types";

/**
 * Candidate view of their own applications with the current pipeline stage.
 * A colored badge maps each stage to an at-a-glance status.
 */
export default function MyApplicationsPage() {
  const [apps, setApps] = useState<MyApplication[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<MyApplication[]>("/applications/my")
      .then(({ data }) => setApps(data))
      .catch((err) => setError(apiError(err, "Could not load your applications")));
  }, []);

  if (error) {
    return <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>;
  }

  if (!apps) {
    return <p className="text-slate-500">Loading…</p>;
  }

  if (apps.length === 0) {
    return (
      <div className="text-center text-slate-500">
        <p className="mb-4">You haven't applied to any jobs yet.</p>
        <Link to="/jobs" className="font-medium text-indigo-600 hover:underline">
          Browse jobs
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold">My Applications</h1>
      <ul className="space-y-3">
        {apps.map((app) => (
          <li
            key={app.id}
            className="flex items-center justify-between rounded-lg border border-slate-200 bg-white p-4"
          >
            <div>
              <Link
                to={`/jobs/${app.job.id}`}
                className="font-semibold text-indigo-600 hover:underline"
              >
                {app.job.title}
              </Link>
              <p className="text-sm text-slate-500">{app.job.company}</p>
              <p className="mt-1 text-xs text-slate-400">
                Applied {new Date(app.appliedAt).toLocaleDateString()}
              </p>
            </div>
            <StageBadge stage={app.stage} reason={app.rejectionReason} />
          </li>
        ))}
      </ul>
    </div>
  );
}

const STAGE_STYLES: Record<string, string> = {
  APPLIED: "bg-slate-100 text-slate-700",
  ATS_REVIEW: "bg-blue-100 text-blue-700",
  ASSESSMENT: "bg-amber-100 text-amber-700",
  INTERVIEW: "bg-purple-100 text-purple-700",
  FINAL: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
  MANUAL_REVIEW: "bg-orange-100 text-orange-700",
};

function StageBadge({ stage, reason }: { stage: string; reason: string | null }) {
  const style = STAGE_STYLES[stage] ?? "bg-slate-100 text-slate-700";
  const label = stage.replace(/_/g, " ");
  return (
    <div className="text-right">
      <span className={`inline-block rounded-full px-3 py-1 text-xs font-medium ${style}`}>
        {label}
      </span>
      {stage === "REJECTED" && reason && (
        <p className="mt-1 text-xs text-slate-400">{reason.replace(/_/g, " ").toLowerCase()}</p>
      )}
    </div>
  );
}
