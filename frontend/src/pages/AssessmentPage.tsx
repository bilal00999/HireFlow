import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import api, { apiError } from "../api/client";
import type {
  AnswerInput,
  AssessmentQuestions,
  SubmitAssessmentResponse,
  VerifyTokenResponse,
} from "../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

type Phase = "verifying" | "gate" | "taking" | "submitted" | "invalid";

/**
 * Token-gated assessment. Candidates arrive from an emailed link, so there's no
 * JWT — the token in the URL is the credential. Flow: verify → gate screen →
 * timed questions → submit. The countdown auto-submits at zero.
 */
export default function AssessmentPage() {
  const { token } = useParams<{ token: string }>();
  const [phase, setPhase] = useState<Phase>("verifying");
  const [verify, setVerify] = useState<VerifyTokenResponse | null>(null);
  const [assessment, setAssessment] = useState<AssessmentQuestions | null>(null);
  const [answers, setAnswers] = useState<Record<string, AnswerInput>>({});
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<SubmitAssessmentResponse | null>(null);
  const submittingRef = useRef(false);

  useEffect(() => {
    if (!token) return;
    api
      .get<VerifyTokenResponse>(`/assessment/verify/${token}`)
      .then(({ data }) => {
        setVerify(data);
        setPhase(data.valid ? "gate" : "invalid");
      })
      .catch((err) => {
        setError(apiError(err, "Could not verify this link"));
        setPhase("invalid");
      });
  }, [token]);

  const submit = useCallback(async () => {
    if (!token || submittingRef.current) return;
    submittingRef.current = true;
    try {
      const payload = { answers: Object.values(answers) };
      const { data } = await api.post<SubmitAssessmentResponse>(
        `/assessment/${token}/submit`,
        payload,
      );
      setResult(data);
      setPhase("submitted");
    } catch (err) {
      setError(apiError(err, "Could not submit your answers"));
      submittingRef.current = false;
    }
  }, [token, answers]);

  // Countdown while taking; auto-submit at zero.
  useEffect(() => {
    if (phase !== "taking") return;
    if (secondsLeft <= 0) {
      submit();
      return;
    }
    const id = setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => clearTimeout(id);
  }, [phase, secondsLeft, submit]);

  async function startAssessment() {
    if (!token) return;
    setError(null);
    try {
      const { data } = await api.get<AssessmentQuestions>(`/assessment/${token}/questions`);
      setAssessment(data);
      setSecondsLeft(data.timeLimit * 60);
      setPhase("taking");
    } catch (err) {
      setError(apiError(err, "Could not load the assessment"));
    }
  }

  function setAnswer(questionId: string, patch: Partial<AnswerInput>) {
    setAnswers((prev) => ({
      ...prev,
      [questionId]: { ...prev[questionId], ...patch, questionId },
    }));
  }

  if (phase === "verifying")
    return <p className="py-16 text-center text-muted-foreground">Verifying your link…</p>;

  if (phase === "invalid") {
    return (
      <div className="mx-auto mt-10 max-w-md rounded-2xl border border-red-500/25 bg-red-500/10 p-6 text-center shadow-card">
        <h1 className="text-lg font-bold text-red-700">This link isn't valid</h1>
        <p className="mt-2 text-sm text-red-600">{error ?? reasonText(verify?.reason)}</p>
      </div>
    );
  }

  if (phase === "submitted") {
    return (
      <div className="mx-auto mt-10 max-w-md rounded-2xl border border-emerald-500/25 bg-emerald-500/10 p-6 text-center shadow-card">
        <h1 className="text-lg font-bold text-emerald-700">Assessment submitted</h1>
        <p className="mt-2 text-sm text-emerald-600">{result?.message}</p>
      </div>
    );
  }

  if (phase === "gate") {
    return (
      <GlassCard hover={false} className="mx-auto mt-10 max-w-md p-6 text-center">
        <h1 className="text-xl font-bold">{verify?.jobTitle} — Assessment</h1>
        <div className="my-6 space-y-1 text-sm text-muted-foreground">
          <p>{verify?.questionCount} questions</p>
          <p>{verify?.timeLimit} minute time limit</p>
        </div>
        <p className="mb-6 text-xs text-muted-foreground/80">
          The timer starts as soon as you begin and can't be paused. Make sure you're ready.
        </p>
        {error && (
          <div className="mb-4 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
            {error}
          </div>
        )}
        <Button variant="gradient" className="w-full" onClick={startAssessment}>
          Start assessment
        </Button>
      </GlassCard>
    );
  }

  // phase === "taking"
  return (
    <div className="mx-auto max-w-2xl">
      <div className="sticky top-4 z-10 mb-6 flex items-center justify-between rounded-2xl border border-border bg-card px-4 py-3 shadow-card">
        <h1 className="font-semibold">{assessment?.jobTitle}</h1>
        <Timer secondsLeft={secondsLeft} />
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      <ol className="space-y-4">
        {assessment?.questions.map((q, i) => (
          <li key={q.id}>
            <GlassCard hover={false} className="p-4">
              <p className="mb-3 font-medium">
                <span className="mr-2 text-muted-foreground">{i + 1}.</span>
                {q.questionText}
              </p>

              {q.questionType === "MCQ" ? (
                <div className="space-y-2">
                  {q.options.map((opt, oi) => (
                    <label
                      key={oi}
                      className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-accent"
                    >
                      <input
                        type="radio"
                        name={q.id}
                        checked={answers[q.id]?.selectedOption === oi}
                        onChange={() => setAnswer(q.id, { selectedOption: oi })}
                        className="size-4 accent-primary"
                      />
                      {opt}
                    </label>
                  ))}
                </div>
              ) : (
                <Textarea
                  rows={4}
                  value={answers[q.id]?.answerText ?? ""}
                  onChange={(e) => setAnswer(q.id, { answerText: e.target.value })}
                />
              )}
            </GlassCard>
          </li>
        ))}
      </ol>

      <Button variant="gradient" className="mt-6 w-full" onClick={submit}>
        Submit assessment
      </Button>
    </div>
  );
}

function Timer({ secondsLeft }: { secondsLeft: number }) {
  const m = Math.floor(secondsLeft / 60);
  const s = secondsLeft % 60;
  const low = secondsLeft <= 60;
  return (
    <span className={`font-mono text-sm font-semibold ${low ? "text-red-600" : "text-foreground"}`}>
      {m}:{s.toString().padStart(2, "0")}
    </span>
  );
}

function reasonText(reason?: string): string {
  switch (reason) {
    case "TOKEN_EXPIRED":
      return "This assessment link has expired.";
    case "TOKEN_USED":
      return "You've already used this assessment link.";
    case "TOKEN_NOT_FOUND":
      return "We couldn't find this assessment.";
    default:
      return "This link is no longer valid.";
  }
}
