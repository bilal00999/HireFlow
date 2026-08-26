import type { QuestionInput } from "../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";

/**
 * Editable list of assessment questions. MCQ questions expose four option
 * inputs and a "correct answer" selector; TEXT questions are free-response and
 * graded by the AI. Fully controlled — the parent owns the array.
 */
export default function QuestionBuilder({
  questions,
  onChange,
}: {
  questions: QuestionInput[];
  onChange: (next: QuestionInput[]) => void;
}) {
  function update(index: number, patch: Partial<QuestionInput>) {
    onChange(questions.map((q, i) => (i === index ? { ...q, ...patch } : q)));
  }

  function addQuestion(type: "MCQ" | "TEXT") {
    onChange([
      ...questions,
      type === "MCQ"
        ? { questionText: "", questionType: "MCQ", options: ["", "", "", ""], correctOption: 0, maxScore: 10 }
        : { questionText: "", questionType: "TEXT", maxScore: 10 },
    ]);
  }

  function remove(index: number) {
    onChange(questions.filter((_, i) => i !== index));
  }

  return (
    <div className="space-y-4">
      {questions.map((q, i) => (
        <GlassCard key={i} hover={false} className="p-4">
          <div className="mb-3 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              {q.questionType === "MCQ" ? "Multiple choice" : "Free text"} · Q{i + 1}
            </span>
            <button
              type="button"
              onClick={() => remove(i)}
              className="text-xs font-medium text-red-600 hover:underline"
            >
              Remove
            </button>
          </div>

          <Textarea
            rows={2}
            value={q.questionText}
            onChange={(e) => update(i, { questionText: e.target.value })}
            placeholder="Question prompt"
            className="mb-3"
          />

          {q.questionType === "MCQ" && (
            <div className="space-y-2">
              {(q.options ?? []).map((opt, oi) => (
                <label key={oi} className="flex items-center gap-2">
                  <input
                    type="radio"
                    name={`correct-${i}`}
                    checked={q.correctOption === oi}
                    onChange={() => update(i, { correctOption: oi })}
                    className="size-4 accent-primary"
                  />
                  <Input
                    value={opt}
                    onChange={(e) => {
                      const options = [...(q.options ?? [])];
                      options[oi] = e.target.value;
                      update(i, { options });
                    }}
                    placeholder={`Option ${oi + 1}`}
                    className="h-9 flex-1"
                  />
                </label>
              ))}
              <p className="text-xs text-muted-foreground">
                Select the radio next to the correct answer.
              </p>
            </div>
          )}

          <div className="mt-3 w-32 space-y-1">
            <label className="block text-xs font-medium text-muted-foreground">Max score</label>
            <Input
              type="number"
              value={q.maxScore ?? ""}
              onChange={(e) => update(i, { maxScore: parseInt(e.target.value, 10) || undefined })}
              className="h-9"
            />
          </div>
        </GlassCard>
      ))}

      <div className="flex gap-3">
        <Button type="button" variant="secondary" onClick={() => addQuestion("MCQ")}>
          + Multiple choice
        </Button>
        <Button type="button" variant="secondary" onClick={() => addQuestion("TEXT")}>
          + Free text
        </Button>
      </div>
    </div>
  );
}
