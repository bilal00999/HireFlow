import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import api, { apiError, interviewSocketUrl } from "../api/client";
import type { VerifyInterviewResponse } from "../api/types";

type Phase = "verifying" | "gate" | "live" | "done" | "invalid";

interface ChatLine {
  from: "ai" | "candidate";
  text: string;
}

/**
 * Token-gated AI interview. The pre-flight check is REST; the interview itself
 * runs over the WebSocket at /ws/interview/{token}. The socket carries JSON
 * frames both ways (START/ANSWER/END out, QUESTION/COMPLETE/ERROR in), and all
 * conversation state lives server-side, so a reconnect resumes where it left off.
 */
export default function InterviewPage() {
  const { token } = useParams<{ token: string }>();
  const [phase, setPhase] = useState<Phase>("verifying");
  const [verify, setVerify] = useState<VerifyInterviewResponse | null>(null);
  const [lines, setLines] = useState<ChatLine[]>([]);
  const [draft, setDraft] = useState("");
  const [waiting, setWaiting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const socketRef = useRef<WebSocket | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!token) return;
    api
      .get<VerifyInterviewResponse>(`/interview/verify/${token}`)
      .then(({ data }) => {
        setVerify(data);
        setPhase(data.valid ? "gate" : "invalid");
      })
      .catch((err) => {
        setError(apiError(err, "Could not verify this link"));
        setPhase("invalid");
      });
  }, [token]);

  // Auto-scroll the transcript as it grows.
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [lines, waiting]);

  // Tidy up the socket on unmount.
  useEffect(() => () => socketRef.current?.close(), []);

  const connect = useCallback(() => {
    if (!token) return;
    setPhase("live");
    setWaiting(true);

    const ws = new WebSocket(interviewSocketUrl(token));
    socketRef.current = ws;

    ws.onopen = () => ws.send(JSON.stringify({ type: "START" }));

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data) as { type: string; content: string };
      setWaiting(false);
      if (msg.type === "QUESTION") {
        setLines((prev) => [...prev, { from: "ai", text: msg.content }]);
      } else if (msg.type === "COMPLETE") {
        setLines((prev) => [...prev, { from: "ai", text: msg.content }]);
        setPhase("done");
        ws.close();
      } else if (msg.type === "ERROR") {
        setError(msg.content);
      }
    };

    ws.onerror = () => {
      setError("Connection lost. Please refresh to resume your interview.");
      setWaiting(false);
    };
  }, [token]);

  function sendAnswer() {
    const text = draft.trim();
    const ws = socketRef.current;
    if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;
    setLines((prev) => [...prev, { from: "candidate", text }]);
    ws.send(JSON.stringify({ type: "ANSWER", content: text }));
    setDraft("");
    setWaiting(true);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends; Shift+Enter adds a newline.
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendAnswer();
    }
  }

  if (phase === "verifying") return <p className="text-slate-500">Verifying your link…</p>;

  if (phase === "invalid") {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-red-200 bg-red-50 p-6 text-center">
        <h1 className="text-lg font-bold text-red-700">This link isn't valid</h1>
        <p className="mt-2 text-sm text-red-600">{error ?? reasonText(verify?.reason)}</p>
      </div>
    );
  }

  if (phase === "gate") {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-slate-200 bg-white p-6 text-center">
        <h1 className="text-xl font-bold">{verify?.jobTitle} — AI Interview</h1>
        <p className="my-6 text-sm text-slate-600">
          This is a conversational interview with our AI. It takes about{" "}
          {verify?.durationMinutes} minutes. Answer naturally — you can't pause once you begin.
        </p>
        <button
          onClick={connect}
          className="w-full rounded-md bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700"
        >
          Begin interview
        </button>
      </div>
    );
  }

  // live or done
  return (
    <div className="mx-auto flex h-[70vh] max-w-2xl flex-col">
      <div
        ref={scrollRef}
        className="flex-1 space-y-4 overflow-y-auto rounded-lg border border-slate-200 bg-white p-4"
      >
        {lines.map((line, i) => (
          <div key={i} className={line.from === "ai" ? "text-left" : "text-right"}>
            <span
              className={`inline-block max-w-[80%] whitespace-pre-line rounded-2xl px-4 py-2 text-sm ${
                line.from === "ai"
                  ? "bg-slate-100 text-slate-800"
                  : "bg-indigo-600 text-white"
              }`}
            >
              {line.text}
            </span>
          </div>
        ))}
        {waiting && <p className="text-sm text-slate-400">Interviewer is typing…</p>}
      </div>

      {error && <div className="mt-2 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}

      {phase === "done" ? (
        <p className="mt-4 rounded-md bg-green-50 px-4 py-3 text-center text-sm font-medium text-green-700">
          Interview complete. The hiring team will be in touch.
        </p>
      ) : (
        <div className="mt-4 flex gap-2">
          <textarea
            rows={2}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type your answer…"
            disabled={waiting}
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
          />
          <button
            onClick={sendAnswer}
            disabled={waiting || !draft.trim()}
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            Send
          </button>
        </div>
      )}
    </div>
  );
}

function reasonText(reason?: string): string {
  switch (reason) {
    case "TOKEN_EXPIRED":
      return "This interview link has expired.";
    case "TOKEN_USED":
      return "You've already completed this interview.";
    case "TOKEN_NOT_FOUND":
      return "We couldn't find this interview.";
    default:
      return "This link is no longer valid.";
  }
}
