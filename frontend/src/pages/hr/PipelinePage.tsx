import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import api, { apiError } from "../../api/client";
import type { Applicant, Pipeline } from "../../api/types";

// Column order for the kanban board — matches the backend pipeline stages.
const STAGE_ORDER = [
  "APPLIED",
  "ATS_REVIEW",
  "ASSESSMENT",
  "INTERVIEW",
  "FINAL",
  "MANUAL_REVIEW",
  "REJECTED",
] as const;

const STAGE_LABELS: Record<string, string> = {
  APPLIED: "Applied",
  ATS_REVIEW: "ATS Review",
  ASSESSMENT: "Assessment",
  INTERVIEW: "Interview",
  FINAL: "Final",
  MANUAL_REVIEW: "Manual Review",
  REJECTED: "Rejected",
};

/** Kanban view of one job's pipeline: a column per stage, applicant cards within. */
export default function PipelinePage() {
  const { jobId } = useParams<{ jobId: string }>();
  const [pipeline, setPipeline] = useState<Pipeline | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!jobId) return;
    api
      .get<Pipeline>(`/hr/pipeline/${jobId}`)
      .then(({ data }) => setPipeline(data))
      .catch((err) => setError(apiError(err, "Could not load pipeline")));
  }, [jobId]);

  if (error) return <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>;
  if (!pipeline) return <p className="text-slate-500">Loading…</p>;

  // Group candidates by stage once, rather than filtering per column.
  const byStage = new Map<string, Applicant[]>();
  for (const c of pipeline.candidates) {
    const list = byStage.get(c.stage) ?? [];
    list.push(c);
    byStage.set(c.stage, list);
  }

  // Only render columns that carry a stage count or have candidates.
  const columns = STAGE_ORDER.filter(
    (s) => (pipeline.stages[s] ?? 0) > 0 || (byStage.get(s)?.length ?? 0) > 0,
  );

  return (
    <div>
      <div className="mb-6">
        <Link to="/hr/dashboard" className="text-sm text-indigo-600 hover:underline">
          ← Dashboard
        </Link>
        <h1 className="mt-2 text-2xl font-bold">{pipeline.jobTitle}</h1>
        <p className="text-sm text-slate-500">{pipeline.candidates.length} applicants</p>
      </div>

      <div className="flex gap-4 overflow-x-auto pb-4">
        {columns.length === 0 && <p className="text-slate-500">No applicants yet.</p>}
        {columns.map((stage) => (
          <div key={stage} className="w-64 shrink-0">
            <div className="mb-2 flex items-center justify-between px-1">
              <h2 className="text-sm font-semibold text-slate-700">{STAGE_LABELS[stage]}</h2>
              <span className="rounded-full bg-slate-200 px-2 text-xs text-slate-600">
                {pipeline.stages[stage] ?? 0}
              </span>
            </div>
            <div className="space-y-2">
              {(byStage.get(stage) ?? []).map((c) => (
                <ApplicantCard key={c.id} applicant={c} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ApplicantCard({ applicant }: { applicant: Applicant }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
      <div className="font-medium text-slate-900">{applicant.candidateName}</div>
      <div className="truncate text-xs text-slate-500">{applicant.candidateEmail}</div>
      {applicant.stage === "REJECTED" && applicant.rejectionReason && (
        <div className="mt-1 text-xs text-red-500">
          {applicant.rejectionReason.replace(/_/g, " ").toLowerCase()}
        </div>
      )}
      {applicant.resumeUrl && (
        <a
          href={applicant.resumeUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-2 inline-block text-xs font-medium text-indigo-600 hover:underline"
        >
          View resume
        </a>
      )}
    </div>
  );
}
