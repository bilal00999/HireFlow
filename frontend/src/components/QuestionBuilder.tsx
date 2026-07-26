import type { QuestionInput } from "../api/types";

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
        <div key={i} className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="mb-3 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-400">
              {q.questionType === "MCQ" ? "Multiple choice" : "Free text"} · Q{i + 1}
            </span>
            <button
              type="button"
              onClick={() => remove(i)}
              className="text-xs font-medium text-red-500 hover:underline"
            >
              Remove
            </button>
          </div>

          <textarea
            rows={2}
            value={q.questionText}
            onChange={(e) => update(i, { questionText: e.target.value })}
            placeholder="Question prompt"
            className="mb-3 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
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
                  />
                  <input
                    value={opt}
                    onChange={(e) => {
                      const options = [...(q.options ?? [])];
                      options[oi] = e.target.value;
                      update(i, { options });
                    }}
                    placeholder={`Option ${oi + 1}`}
                    className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
                  />
                </label>
              ))}
              <p className="text-xs text-slate-400">Select the radio next to the correct answer.</p>
            </div>
          )}

          <div className="mt-3 w-32">
            <label className="block text-xs font-medium text-slate-500">Max score</label>
            <input
              type="number"
              value={q.maxScore ?? ""}
              onChange={(e) => update(i, { maxScore: parseInt(e.target.value, 10) || undefined })}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm"
            />
          </div>
        </div>
      ))}

      <div className="flex gap-3">
        <button
          type="button"
          onClick={() => addQuestion("MCQ")}
          className="rounded-md bg-slate-100 px-4 py-2 text-sm font-medium hover:bg-slate-200"
        >
          + Multiple choice
        </button>
        <button
          type="button"
          onClick={() => addQuestion("TEXT")}
          className="rounded-md bg-slate-100 px-4 py-2 text-sm font-medium hover:bg-slate-200"
        >
          + Free text
        </button>
      </div>
    </div>
  );
}
