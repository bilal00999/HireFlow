import { useState, type FormEvent } from "react";
import api, { apiError } from "../api/client";
import type { ApplyResponse } from "../api/types";

/**
 * Resume + optional cover letter, posted as multipart/form-data to
 * POST /applications. On success the parent flips the job to "applied".
 */
export default function ApplyModal({
  jobId,
  jobTitle,
  onClose,
  onSuccess,
}: {
  jobId: string;
  jobTitle: string;
  onClose: () => void;
  onSuccess: (res: ApplyResponse) => void;
}) {
  const [resume, setResume] = useState<File | null>(null);
  const [coverLetter, setCoverLetter] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!resume) {
      setError("Please attach your resume");
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const form = new FormData();
      form.append("jobId", jobId);
      form.append("resume", resume);
      if (coverLetter.trim()) form.append("coverLetter", coverLetter.trim());
      const { data } = await api.post<ApplyResponse>("/applications", form);
      onSuccess(data);
    } catch (err) {
      setError(apiError(err, "Could not submit application"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-bold">Apply for {jobTitle}</h2>
        <p className="mt-1 text-sm text-slate-500">
          Attach your resume as a PDF or Word document.
        </p>

        {error && (
          <div className="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Resume</span>
            <input
              type="file"
              accept=".pdf,.doc,.docx"
              required
              onChange={(e) => setResume(e.target.files?.[0] ?? null)}
              className="w-full text-sm file:mr-3 file:rounded-md file:border-0 file:bg-indigo-50 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-indigo-700"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Cover letter (optional)
            </span>
            <textarea
              rows={4}
              value={coverLetter}
              onChange={(e) => setCoverLetter(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </label>

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="rounded-md bg-slate-100 px-4 py-2 text-sm font-medium hover:bg-slate-200"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy}
              className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
            >
              {busy ? "Submitting…" : "Submit application"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
