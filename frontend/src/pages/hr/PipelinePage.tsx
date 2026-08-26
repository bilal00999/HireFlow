import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import api, { apiError } from "../../api/client";
import type { Applicant, Pipeline } from "../../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { Skeleton } from "@/components/ui/skeleton";

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

  if (error)
    return (
      <div className="rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
        {error}
      </div>
    );
  if (!pipeline)
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-64" />
        <div className="flex gap-4">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-64 w-64 shrink-0 rounded-2xl" />
          ))}
        </div>
      </div>
    );

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
        <Link
          to="/hr/dashboard"
          className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
        >
          <ArrowLeft className="size-4" />
          Dashboard
        </Link>
        <h1 className="mt-2 text-2xl font-bold">{pipeline.jobTitle}</h1>
        <p className="text-sm text-muted-foreground">
          {pipeline.candidates.length} applicants
        </p>
      </div>

      <div className="flex gap-4 overflow-x-auto pb-4">
        {columns.length === 0 && (
          <p className="text-muted-foreground">No applicants yet.</p>
        )}
        {columns.map((stage) => (
          <div key={stage} className="w-64 shrink-0">
            <div className="mb-2 flex items-center justify-between px-1">
              <h2 className="text-sm font-semibold">{STAGE_LABELS[stage]}</h2>
              <span className="rounded-full bg-secondary px-2 py-0.5 text-xs font-medium text-muted-foreground">
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
  // The whole card opens the candidate's scorecard; the resume link stops
  // propagation so it opens the PDF instead of navigating.
  return (
    <Link to={`/hr/candidate/${applicant.id}`} className="block">
      <GlassCard className="p-3">
        <div className="font-medium">{applicant.candidateName}</div>
        <div className="truncate text-xs text-muted-foreground">
          {applicant.candidateEmail}
        </div>
        {applicant.stage === "REJECTED" && applicant.rejectionReason && (
          <div className="mt-1 text-xs text-red-600">
            {applicant.rejectionReason.replace(/_/g, " ").toLowerCase()}
          </div>
        )}
        {applicant.resumeUrl && (
          <a
            href={applicant.resumeUrl}
            target="_blank"
            rel="noreferrer"
            onClick={(e) => e.stopPropagation()}
            className="mt-2 inline-block text-xs font-medium text-primary hover:underline"
          >
            View resume
          </a>
        )}
      </GlassCard>
    </Link>
  );
}
