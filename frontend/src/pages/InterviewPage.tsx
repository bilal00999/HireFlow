import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import api, { apiError, interviewSocketUrl } from "../api/client";
import type { VerifyInterviewResponse } from "../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

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

  if (phase === "gate") {
    return (
      <GlassCard hover={false} className="mx-auto mt-10 max-w-md p-6 text-center">
        <h1 className="text-xl font-bold">{verify?.jobTitle} — AI Interview</h1>
        <p className="my-6 text-sm text-muted-foreground">
          This is a conversational interview with our AI. It takes about{" "}
          {verify?.durationMinutes} minutes. Answer naturally — you can't pause once you begin.
        </p>
        <Button variant="gradient" className="w-full" onClick={connect}>
          Begin interview
        </Button>
      </GlassCard>
    );
  }

  // live or done
  return (
    <div className="mx-auto flex h-[70vh] max-w-2xl flex-col">
      <div
        ref={scrollRef}
        className="flex-1 space-y-4 overflow-y-auto rounded-2xl border border-border bg-card p-4 shadow-card"
      >
        {lines.map((line, i) => (
          <div key={i} className={line.from === "ai" ? "text-left" : "text-right"}>
            <span
              className={`inline-block max-w-[80%] whitespace-pre-line rounded-2xl px-4 py-2 text-sm ${
                line.from === "ai"
                  ? "bg-secondary text-secondary-foreground"
                  : "bg-primary text-primary-foreground"
              }`}
            >
              {line.text}
            </span>
          </div>
        ))}
        {waiting && <p className="text-sm text-muted-foreground">Interviewer is typing…</p>}
      </div>

      {error && (
        <div className="mt-2 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      {phase === "done" ? (
        <p className="mt-4 rounded-lg border border-emerald-500/25 bg-emerald-500/10 px-4 py-3 text-center text-sm font-medium text-emerald-700">
          Interview complete. The hiring team will be in touch.
        </p>
      ) : (
        <div className="mt-4 flex gap-2">
          <Textarea
            rows={2}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type your answer…"
            disabled={waiting}
            className="flex-1 resize-none"
          />
          <Button onClick={sendAnswer} disabled={waiting || !draft.trim()}>
            Send
          </Button>
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
