import { useState, type FormEvent } from "react";
import { Loader2, AlertCircle } from "lucide-react";
import api, { apiError } from "../api/client";
import type { ApplyResponse } from "../api/types";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";

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
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Apply for {jobTitle}</DialogTitle>
          <DialogDescription>
            Attach your resume as a PDF or Word document.
          </DialogDescription>
        </DialogHeader>

        {error && (
          <div className="flex items-center gap-2 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
            <AlertCircle className="size-4 shrink-0" />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="resume">Resume</Label>
            <input
              id="resume"
              type="file"
              accept=".pdf,.doc,.docx"
              required
              onChange={(e) => setResume(e.target.files?.[0] ?? null)}
              className="w-full text-sm text-muted-foreground file:mr-3 file:rounded-md file:border-0 file:bg-primary/10 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-primary hover:file:bg-primary/15"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="coverLetter">Cover letter (optional)</Label>
            <Textarea
              id="coverLetter"
              rows={4}
              value={coverLetter}
              onChange={(e) => setCoverLetter(e.target.value)}
            />
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" variant="gradient" disabled={busy}>
              {busy && <Loader2 className="size-4 animate-spin" />}
              {busy ? "Submitting…" : "Submit application"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
