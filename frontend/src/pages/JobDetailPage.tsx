import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import {
  Building2,
  MapPin,
  Briefcase,
  CalendarClock,
  Wallet,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";

import api, { apiError } from "../api/client";
import { useAuth } from "../api/auth";
import type { JobDetail } from "../api/types";
import ApplyModal from "../components/ApplyModal";
import { GlassCard } from "@/components/ui/glass-card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

/** Public job detail with an Apply action gated to logged-in candidates. */
export default function JobDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, isCandidate } = useAuth();

  const [job, setJob] = useState<JobDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showApply, setShowApply] = useState(false);
  const [applied, setApplied] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    api
      .get<JobDetail>(`/jobs/${id}`)
      .then(({ data }) => !cancelled && setJob(data))
      .catch((err) => !cancelled && setError(apiError(err, "Job not found")))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (loading)
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <Skeleton className="h-32 w-full rounded-2xl" />
        <Skeleton className="h-48 w-full rounded-2xl" />
      </div>
    );
  if (error)
    return (
      <div className="mx-auto flex max-w-3xl items-center gap-2 rounded-lg border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-300">
        <AlertCircle className="size-4 shrink-0" />
        {error}
      </div>
    );
  if (!job) return null;

  function handleApplyClick() {
    if (!isAuthenticated) {
      navigate("/login");
      return;
    }
    setShowApply(true);
  }

  return (
    <motion.article
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
      className="mx-auto max-w-3xl"
    >
      <GlassCard hover={false} className="mb-6 p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold">{job.title}</h1>
            <p className="mt-1 flex items-center gap-1.5 text-muted-foreground">
              <Building2 className="size-4" />
              {job.company}
            </p>
          </div>
          {isCandidate && !applied && (
            <Button onClick={handleApplyClick} variant="gradient" className="shrink-0">
              Apply now
            </Button>
          )}
          {applied && (
            <Badge variant="success" className="shrink-0 px-3 py-1.5">
              <CheckCircle2 className="size-3.5" />
              Application submitted
            </Badge>
          )}
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          {job.jobType && (
            <Badge variant="info">
              <Briefcase className="size-3.5" />
              {job.jobType.replace("_", " ")}
            </Badge>
          )}
          {job.location && (
            <Badge variant="secondary">
              <MapPin className="size-3.5" />
              {job.location}
            </Badge>
          )}
          {job.salaryMin != null && job.salaryMax != null && (
            <Badge variant="secondary">
              <Wallet className="size-3.5" />
              {job.currency ?? "USD"} {job.salaryMin.toLocaleString()}–
              {job.salaryMax.toLocaleString()}
            </Badge>
          )}
          {job.deadline && (
            <Badge variant="warning">
              <CalendarClock className="size-3.5" />
              Apply by {job.deadline}
            </Badge>
          )}
        </div>
      </GlassCard>

      <Section title="Description">{job.description}</Section>
      {job.requirements && <Section title="Requirements">{job.requirements}</Section>}

      {job.requiredSkills.length > 0 && (
        <section className="mt-6">
          <h2 className="mb-3 font-semibold">Required skills</h2>
          <div className="flex flex-wrap gap-2">
            {job.requiredSkills.map((skill) => (
              <Badge key={skill} variant="default">
                {skill}
              </Badge>
            ))}
          </div>
        </section>
      )}

      {showApply && (
        <ApplyModal
          jobId={job.id}
          jobTitle={job.title}
          onClose={() => setShowApply(false)}
          onSuccess={() => {
            setShowApply(false);
            setApplied(true);
          }}
        />
      )}
    </motion.article>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mt-6">
      <h2 className="mb-2 font-semibold">{title}</h2>
      <p className="whitespace-pre-line leading-relaxed text-muted-foreground">
        {children}
      </p>
    </section>
  );
}
