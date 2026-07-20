import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import api, { apiError } from "../api/client";
import { useAuth } from "../api/auth";
import type { JobDetail } from "../api/types";
import ApplyModal from "../components/ApplyModal";

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

  if (loading) return <p className="text-slate-500">Loading…</p>;
  if (error) return <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>;
  if (!job) return null;

  function handleApplyClick() {
    if (!isAuthenticated) {
      navigate("/login");
      return;
    }
    setShowApply(true);
  }

  return (
    <article>
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">{job.title}</h1>
          <p className="mt-1 text-slate-600">{job.company}</p>
          <div className="mt-2 flex flex-wrap gap-x-4 text-sm text-slate-500">
            {job.jobType && <span>{job.jobType.replace("_", " ")}</span>}
            {job.location && <span>{job.location}</span>}
            {job.salaryMin != null && job.salaryMax != null && (
              <span>
                {job.currency ?? "USD"} {job.salaryMin.toLocaleString()}–
                {job.salaryMax.toLocaleString()}
              </span>
            )}
            {job.deadline && <span>Apply by {job.deadline}</span>}
          </div>
        </div>

        {isCandidate && !applied && (
          <button
            onClick={handleApplyClick}
            className="shrink-0 rounded-md bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700"
          >
            Apply now
          </button>
        )}
        {applied && (
          <span className="shrink-0 rounded-md bg-green-50 px-4 py-2 text-sm font-medium text-green-700">
            Application submitted
          </span>
        )}
      </div>

      <Section title="Description">{job.description}</Section>
      {job.requirements && <Section title="Requirements">{job.requirements}</Section>}

      {job.requiredSkills.length > 0 && (
        <section className="mt-6">
          <h2 className="mb-2 font-semibold">Required skills</h2>
          <div className="flex flex-wrap gap-2">
            {job.requiredSkills.map((skill) => (
              <span
                key={skill}
                className="rounded-full bg-indigo-50 px-3 py-1 text-sm text-indigo-700"
              >
                {skill}
              </span>
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
    </article>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mt-6">
      <h2 className="mb-2 font-semibold">{title}</h2>
      <p className="whitespace-pre-line text-slate-700">{children}</p>
    </section>
  );
}
