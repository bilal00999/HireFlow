import { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Loader2 } from "lucide-react";
import api, { apiError } from "../../api/client";
import type { CandidateDetail, DecisionKind } from "../../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { StageBadge } from "@/components/StageBadge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";

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
    return (
      <div className="rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
        {error}
      </div>
    );
  }
  if (!detail) return <p className="text-muted-foreground">Loading…</p>;

  const decided = detail.stage === "HIRED" || detail.stage === "REJECTED";

  return (
    <div className="mx-auto max-w-3xl">
      <button
        onClick={() => navigate(-1)}
        className="mb-4 inline-flex items-center gap-1 text-sm text-primary hover:underline"
      >
        <ArrowLeft className="size-4" />
        Back
      </button>

      <header className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">{detail.candidateName}</h1>
          <p className="text-muted-foreground">{detail.candidateEmail}</p>
          <Link
            to={`/hr/pipeline/${detail.jobId}`}
            className="text-sm text-primary hover:underline"
          >
            {detail.jobTitle}
          </Link>
        </div>
        <StageBadge stage={detail.stage} className="shrink-0 px-3 py-1 text-sm" />
      </header>

      {error && (
        <div className="mb-4 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* Score summary across all stages */}
      <section className="mb-6 grid grid-cols-3 gap-4">
        <ScoreCard label="ATS Resume" score={detail.ats?.score ?? null} passed={detail.ats?.passed ?? null} />
        <ScoreCard label="Assessment" score={detail.assessmentScore ?? null} passed={null} />
        <ScoreCard label="AI Interview" score={detail.interview?.score ?? null} passed={detail.interview?.passed ?? null} />
      </section>

      {detail.ats && (
        <Panel title="Resume screen">
          {detail.ats.summary && (
            <p className="mb-3 text-sm text-muted-foreground">{detail.ats.summary}</p>
          )}
          <SkillRow label="Matched" skills={detail.ats.matchedSkills} tone="success" />
          <SkillRow label="Missing" skills={detail.ats.missingSkills} tone="destructive" />
          {detail.resumeUrl && (
            <a
              href={detail.resumeUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-2 inline-block text-sm font-medium text-primary hover:underline"
            >
              View resume
            </a>
          )}
        </Panel>
      )}

      {detail.interview && (
        <Panel title="AI interview report">
          {detail.interview.recommendation && (
            <p className="mb-2 text-sm">
              <span className="font-semibold">Recommendation:</span>{" "}
              {detail.interview.recommendation}
            </p>
          )}
          {detail.interview.summary && (
            <p className="mb-3 text-sm text-muted-foreground">{detail.interview.summary}</p>
          )}
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
                <span
                  className={`inline-block max-w-[85%] whitespace-pre-line rounded-2xl px-3 py-2 text-sm ${
                    m.role === "ai"
                      ? "bg-secondary text-secondary-foreground"
                      : "bg-primary text-primary-foreground"
                  }`}
                >
                  {m.content}
                </span>
              </div>
            ))}
          </div>
        </Panel>
      )}

      {/* Decision box — only when the candidate has cleared the pipeline */}
      {detail.stage === "FINAL" && (
        <GlassCard hover={false} className="mt-6 bg-primary/5 p-4 ring-1 ring-primary/15">
          <h2 className="mb-2 font-semibold">Make a decision</h2>
          <Textarea
            rows={2}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Optional note to include in the candidate's email"
            className="mb-3"
          />
          <div className="flex gap-3">
            <Button variant="success" onClick={() => setDecision("HIRE")}>
              Hire
            </Button>
            <Button variant="destructive" onClick={() => setDecision("REJECT")}>
              Reject
            </Button>
          </div>
        </GlassCard>
      )}

      {decided && (
        <p className="mt-6 rounded-lg bg-secondary px-4 py-3 text-center text-sm font-medium text-muted-foreground">
          Decision recorded: {detail.stage}.
        </p>
      )}

      {/* Confirm dialog */}
      <Dialog open={!!decision} onOpenChange={(open) => !open && !busy && setDecision(null)}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>
              {decision === "HIRE" ? "Hire this candidate?" : "Reject this candidate?"}
            </DialogTitle>
            <DialogDescription>
              {detail.candidateName} will be emailed{" "}
              {decision === "HIRE" ? "an offer message" : "a rejection message"}.
              {note.trim() && " Your note will be included."}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDecision(null)} disabled={busy}>
              Cancel
            </Button>
            <Button
              variant={decision === "HIRE" ? "success" : "destructive"}
              onClick={submitDecision}
              disabled={busy}
            >
              {busy && <Loader2 className="size-4 animate-spin" />}
              {busy ? "Submitting…" : "Confirm"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function ScoreCard({
  label,
  score,
  passed,
}: {
  label: string;
  score: number | null;
  passed: boolean | null;
}) {
  return (
    <GlassCard hover={false} className="p-4 text-center">
      <div className="text-2xl font-bold">{score != null ? `${score}` : "—"}</div>
      <div className="mt-1 text-xs text-muted-foreground">{label}</div>
      {passed != null && (
        <Badge variant={passed ? "success" : "destructive"} className="mt-2">
          {passed ? "passed" : "failed"}
        </Badge>
      )}
    </GlassCard>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <GlassCard hover={false} className="mb-4 p-4">
      <h2 className="mb-3 font-semibold">{title}</h2>
      {children}
    </GlassCard>
  );
}

function SkillRow({
  label,
  skills,
  tone,
}: {
  label: string;
  skills: string[];
  tone: "success" | "destructive";
}) {
  if (!skills || skills.length === 0) return null;
  return (
    <div className="mb-2 flex flex-wrap items-center gap-1">
      <span className="mr-1 text-xs font-medium text-muted-foreground">{label}:</span>
      {skills.map((s) => (
        <Badge key={s} variant={tone}>
          {s}
        </Badge>
      ))}
    </div>
  );
}

function BulletBlock({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="mb-2">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {title}
      </p>
      <ul className="mt-1 list-inside list-disc text-sm text-muted-foreground">
        {items.map((it, i) => (
          <li key={i}>{it}</li>
        ))}
      </ul>
    </div>
  );
}
