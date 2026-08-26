import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { PlusCircle, Briefcase } from "lucide-react";
import api, { apiError } from "../../api/client";
import type { DashboardStats, JobSummary } from "../../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

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
        <Button asChild variant="gradient">
          <Link to="/hr/jobs/new">
            <PlusCircle className="size-4" />
            Post a job
          </Link>
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="mb-8 grid grid-cols-2 gap-4 sm:grid-cols-5">
        <StatCard label="Active jobs" value={stats?.activeJobs} />
        <StatCard label="Applications" value={stats?.totalApplications} />
        <StatCard label="In assessment" value={stats?.inAssessment} />
        <StatCard label="In interview" value={stats?.inInterview} />
        <StatCard label="Ready for review" value={stats?.readyForReview} highlight />
      </div>

      <h2 className="mb-3 text-lg font-semibold">Your jobs</h2>
      {jobs === null && (
        <div className="space-y-3">
          {[0, 1].map((i) => (
            <Skeleton key={i} className="h-16 w-full rounded-2xl" />
          ))}
        </div>
      )}
      {jobs?.length === 0 && (
        <GlassCard hover={false} className="py-12 text-center">
          <Briefcase className="mx-auto mb-3 size-8 text-muted-foreground" />
          <p className="text-muted-foreground">No jobs yet. Post one to start hiring.</p>
        </GlassCard>
      )}
      <ul className="space-y-3">
        {jobs?.map((job, i) => (
          <motion.li
            key={job.id}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: Math.min(i * 0.04, 0.3) }}
          >
            <Link to={`/hr/pipeline/${job.id}`} className="block">
              <GlassCard className="flex items-center justify-between p-4">
                <div>
                  <span className="font-semibold">{job.title}</span>
                  {job.location && (
                    <span className="ml-3 text-sm text-muted-foreground">
                      {job.location}
                    </span>
                  )}
                </div>
                <StatusPill status={job.status} />
              </GlassCard>
            </Link>
          </motion.li>
        ))}
      </ul>
    </div>
  );
}

function StatCard({
  label,
  value,
  highlight,
}: {
  label: string;
  value?: number;
  highlight?: boolean;
}) {
  return (
    <GlassCard
      hover={false}
      className={`p-4 ${highlight ? "bg-primary/5 ring-1 ring-primary/20" : ""}`}
    >
      <div className={`text-2xl font-bold ${highlight ? "text-primary" : ""}`}>
        {value ?? "—"}
      </div>
      <div className="mt-1 text-xs text-muted-foreground">{label}</div>
    </GlassCard>
  );
}

function StatusPill({ status }: { status: string }) {
  const variant =
    status === "ACTIVE" ? "success" : status === "DRAFT" ? "muted" : "warning";
  return <Badge variant={variant}>{status.toLowerCase()}</Badge>;
}
