import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import api, { apiError } from "../api/client";
import type {
  AnswerInput,
  AssessmentQuestions,
  SubmitAssessmentResponse,
  VerifyTokenResponse,
} from "../api/types";

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

  if (phase === "verifying") return <p className="text-slate-500">Verifying your link…</p>;

  if (phase === "invalid") {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-red-200 bg-red-50 p-6 text-center">
        <h1 className="text-lg font-bold text-red-700">This link isn't valid</h1>
        <p className="mt-2 text-sm text-red-600">
          {error ?? reasonText(verify?.reason)}
        </p>
      </div>
    );
  }

  if (phase === "submitted") {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-green-200 bg-green-50 p-6 text-center">
        <h1 className="text-lg font-bold text-green-700">Assessment submitted</h1>
        <p className="mt-2 text-sm text-green-600">{result?.message}</p>
      </div>
    );
  }

  if (phase === "gate") {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-slate-200 bg-white p-6 text-center">
        <h1 className="text-xl font-bold">{verify?.jobTitle} — Assessment</h1>
        <div className="my-6 space-y-1 text-sm text-slate-600">
          <p>{verify?.questionCount} questions</p>
          <p>{verify?.timeLimit} minute time limit</p>
        </div>
        <p className="mb-6 text-xs text-slate-400">
          The timer starts as soon as you begin and can't be paused. Make sure you're ready.
        </p>
        {error && <div className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
        <button
          onClick={startAssessment}
          className="w-full rounded-md bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700"
        >
          Start assessment
        </button>
      </div>
    );
  }

  // phase === "taking"
  return (
    <div className="mx-auto max-w-2xl">
      <div className="sticky top-0 z-10 mb-6 flex items-center justify-between rounded-md border border-slate-200 bg-white px-4 py-3">
        <h1 className="font-semibold">{assessment?.jobTitle}</h1>
        <Timer secondsLeft={secondsLeft} />
      </div>

      {error && <div className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}

      <ol className="space-y-6">
        {assessment?.questions.map((q, i) => (
          <li key={q.id} className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="mb-3 font-medium">
              <span className="mr-2 text-slate-400">{i + 1}.</span>
              {q.questionText}
            </p>

            {q.questionType === "MCQ" ? (
              <div className="space-y-2">
                {q.options.map((opt, oi) => (
                  <label key={oi} className="flex items-center gap-2 text-sm">
                    <input
                      type="radio"
                      name={q.id}
                      checked={answers[q.id]?.selectedOption === oi}
                      onChange={() => setAnswer(q.id, { selectedOption: oi })}
                    />
                    {opt}
                  </label>
                ))}
              </div>
            ) : (
              <textarea
                rows={4}
                value={answers[q.id]?.answerText ?? ""}
                onChange={(e) => setAnswer(q.id, { answerText: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            )}
          </li>
        ))}
      </ol>

      <button
        onClick={submit}
        className="mt-6 w-full rounded-md bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700"
      >
        Submit assessment
      </button>
    </div>
  );
}

function Timer({ secondsLeft }: { secondsLeft: number }) {
  const m = Math.floor(secondsLeft / 60);
  const s = secondsLeft % 60;
  const low = secondsLeft <= 60;
  return (
    <span className={`font-mono text-sm font-semibold ${low ? "text-red-600" : "text-slate-700"}`}>
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
