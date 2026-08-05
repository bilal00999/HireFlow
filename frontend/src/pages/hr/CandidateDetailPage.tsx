import { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import api, { apiError } from "../../api/client";
import type { CandidateDetail, DecisionKind } from "../../api/types";

/**
 * HR scorecard for one candidate: every stage's score, the AI interview report,
 * and the full transcript. When the candidate is at FINAL, HR can hire or reject
 * here (POST /applications/:id/decision) — the one place the pipeline's last
 * human step happens.
 */
export default function CandidateDetailPage() {
  const { applicationId } = useParams<{ applicationId: string }>();
  const navigate = useNavigate();

  const [detail, setDetail] = useState<CandidateDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [decision, setDecision] = useState<DecisionKind | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!applicationId) return;
    api
      .get<CandidateDetail>(`/hr/candidate/${applicationId}`)
      .then(({ data }) => setDetail(data))
      .catch((err) => setError(apiError(err, "Could not load candidate")));
  }, [applicationId]);

  async function submitDecision() {
    if (!applicationId || !decision) return;
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post<CandidateDetail>(
        `/applications/${applicationId}/decision`,
        { decision, note: note.trim() || undefined },
      );
      // The decision response echoes the updated application stage.
      setDetail((prev) => (prev ? { ...prev, stage: data.stage } : prev));
      setDecision(null);
      setNote("");
    } catch (err) {
      setError(apiError(err, "Could not submit decision"));
    } finally {
      setBusy(false);
    }
  }

  if (error && !detail) {
    return <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>;
  }
  if (!detail) return <p className="text-slate-500">Loading…</p>;

  const decided = detail.stage === "HIRED" || detail.stage === "REJECTED";

  return (
    <div className="mx-auto max-w-3xl">
      <button
        onClick={() => navigate(-1)}
        className="mb-4 text-sm text-indigo-600 hover:underline"
      >
        ← Back
      </button>

      <header className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">{detail.candidateName}</h1>
          <p className="text-slate-600">{detail.candidateEmail}</p>
          <Link to={`/hr/pipeline/${detail.jobId}`} className="text-sm text-indigo-600 hover:underline">
            {detail.jobTitle}
          </Link>
        </div>
        <StageBadge stage={detail.stage} />
      </header>

      {error && <div className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}

      {/* Score summary across all stages */}
      <section className="mb-6 grid grid-cols-3 gap-4">
        <ScoreCard label="ATS Resume" score={detail.ats?.score ?? null} passed={detail.ats?.passed ?? null} />
        <ScoreCard label="Assessment" score={detail.assessmentScore ?? null} passed={null} />
        <ScoreCard label="AI Interview" score={detail.interview?.score ?? null} passed={detail.interview?.passed ?? null} />
      </section>

      {detail.ats && (
        <Panel title="Resume screen">
          {detail.ats.summary && <p className="mb-3 text-sm text-slate-700">{detail.ats.summary}</p>}
          <SkillRow label="Matched" skills={detail.ats.matchedSkills} tone="green" />
          <SkillRow label="Missing" skills={detail.ats.missingSkills} tone="red" />
          {detail.resumeUrl && (
            <a href={detail.resumeUrl} target="_blank" rel="noreferrer"
              className="mt-2 inline-block text-sm font-medium text-indigo-600 hover:underline">
              View resume
            </a>
          )}
        </Panel>
      )}

      {detail.interview && (
        <Panel title="AI interview report">
          {detail.interview.recommendation && (
            <p className="mb-2 text-sm">
              <span className="font-semibold">Recommendation:</span> {detail.interview.recommendation}
            </p>
          )}
          {detail.interview.summary && <p className="mb-3 text-sm text-slate-700">{detail.interview.summary}</p>}
          {detail.interview.strengths && detail.interview.strengths.length > 0 && (
            <BulletBlock title="Strengths" items={detail.interview.strengths} />
          )}
          {detail.interview.weaknesses && detail.interview.weaknesses.length > 0 && (
            <BulletBlock title="Areas for improvement" items={detail.interview.weaknesses} />
          )}
        </Panel>
      )}

      {detail.transcript.length > 0 && (
        <Panel title="Interview transcript">
          <div className="space-y-3">
            {detail.transcript.map((m) => (
              <div key={m.order} className={m.role === "ai" ? "text-left" : "text-right"}>
                <span className={`inline-block max-w-[85%] whitespace-pre-line rounded-2xl px-3 py-2 text-sm ${
                  m.role === "ai" ? "bg-slate-100 text-slate-800" : "bg-indigo-600 text-white"
                }`}>
                  {m.content}
                </span>
              </div>
            ))}
          </div>
        </Panel>
      )}

      {/* Decision box — only when the candidate has cleared the pipeline */}
      {detail.stage === "FINAL" && (
        <section className="mt-6 rounded-lg border border-indigo-200 bg-indigo-50 p-4">
          <h2 className="mb-2 font-semibold">Make a decision</h2>
          <textarea
            rows={2}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Optional note to include in the candidate's email"
            className="mb-3 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          <div className="flex gap-3">
            <button
              onClick={() => setDecision("HIRE")}
              className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700"
            >
              Hire
            </button>
            <button
              onClick={() => setDecision("REJECT")}
              className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
            >
              Reject
            </button>
          </div>
        </section>
      )}

      {decided && (
        <p className="mt-6 rounded-md bg-slate-100 px-4 py-3 text-center text-sm font-medium text-slate-600">
          Decision recorded: {detail.stage}.
        </p>
      )}

      {/* Confirm dialog */}
      {decision && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          onClick={() => !busy && setDecision(null)}>
          <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-lg font-bold">
              {decision === "HIRE" ? "Hire this candidate?" : "Reject this candidate?"}
            </h3>
            <p className="mt-2 text-sm text-slate-500">
              {detail.candidateName} will be emailed{" "}
              {decision === "HIRE" ? "an offer message" : "a rejection message"}.
              {note.trim() && " Your note will be included."}
            </p>
            <div className="mt-4 flex justify-end gap-3">
              <button onClick={() => setDecision(null)} disabled={busy}
                className="rounded-md bg-slate-100 px-4 py-2 text-sm font-medium hover:bg-slate-200">
                Cancel
              </button>
              <button onClick={submitDecision} disabled={busy}
                className={`rounded-md px-4 py-2 text-sm font-medium text-white disabled:opacity-60 ${
                  decision === "HIRE" ? "bg-green-600 hover:bg-green-700" : "bg-red-600 hover:bg-red-700"
                }`}>
                {busy ? "Submitting…" : "Confirm"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ScoreCard({ label, score, passed }: { label: string; score: number | null; passed: boolean | null }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 text-center">
      <div className="text-2xl font-bold text-slate-900">{score != null ? `${score}` : "—"}</div>
      <div className="mt-1 text-xs text-slate-500">{label}</div>
      {passed != null && (
        <span className={`mt-2 inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
          passed ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"
        }`}>
          {passed ? "passed" : "failed"}
        </span>
      )}
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-4 rounded-lg border border-slate-200 bg-white p-4">
      <h2 className="mb-3 font-semibold">{title}</h2>
      {children}
    </section>
  );
}

function SkillRow({ label, skills, tone }: { label: string; skills: string[]; tone: "green" | "red" }) {
  if (!skills || skills.length === 0) return null;
  const cls = tone === "green" ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700";
  return (
    <div className="mb-2">
      <span className="mr-2 text-xs font-medium text-slate-500">{label}:</span>
      {skills.map((s) => (
        <span key={s} className={`mr-1 inline-block rounded-full px-2 py-0.5 text-xs ${cls}`}>{s}</span>
      ))}
    </div>
  );
}

function BulletBlock({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="mb-2">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{title}</p>
      <ul className="mt-1 list-inside list-disc text-sm text-slate-700">
        {items.map((it, i) => <li key={i}>{it}</li>)}
      </ul>
    </div>
  );
}

function StageBadge({ stage }: { stage: string }) {
  const styles: Record<string, string> = {
    HIRED: "bg-green-100 text-green-700",
    REJECTED: "bg-red-100 text-red-700",
    FINAL: "bg-purple-100 text-purple-700",
  };
  const style = styles[stage] ?? "bg-slate-100 text-slate-600";
  return (
    <span className={`shrink-0 rounded-full px-3 py-1 text-sm font-medium ${style}`}>
      {stage.replace(/_/g, " ")}
    </span>
  );
}
