import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Loader2 } from "lucide-react";
import api, { apiError } from "../../api/client";
import type { CreateJobRequest, JobDetail, QuestionInput } from "../../api/types";
import QuestionBuilder from "../../components/QuestionBuilder";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from "@/components/ui/select";

type Step = 1 | 2 | 3;

/**
 * Three-step job creation:
 *   1. Basics (title, description, skills, location, salary)
 *   2. Pipeline thresholds (ATS score, assessment, interview config)
 *   3. Assessment questions
 * On submit: POST /jobs, then POST /jobs/:id/questions (if any), then publish
 * via PATCH /jobs/:id/status so the job goes live immediately.
 */
export default function CreateJobPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>(1);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Step 1
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [requirements, setRequirements] = useState("");
  const [skills, setSkills] = useState("");
  const [jobType, setJobType] = useState("FULL_TIME");
  const [location, setLocation] = useState("");
  const [salaryMin, setSalaryMin] = useState("");
  const [salaryMax, setSalaryMax] = useState("");
  const [deadline, setDeadline] = useState("");

  // Step 2
  const [atsMinScore, setAtsMinScore] = useState("60");
  const [assessmentPassScore, setAssessmentPassScore] = useState("70");
  const [assessmentTimeLimit, setAssessmentTimeLimit] = useState("30");
  const [interviewTopics, setInterviewTopics] = useState("");
  const [interviewDuration, setInterviewDuration] = useState("20");
  const [interviewNumQuestions, setInterviewNumQuestions] = useState("5");

  // Step 3
  const [questions, setQuestions] = useState<QuestionInput[]>([]);

  function num(v: string): number | undefined {
    const n = parseInt(v, 10);
    return Number.isNaN(n) ? undefined : n;
  }

  function list(v: string): string[] | undefined {
    const arr = v.split(",").map((s) => s.trim()).filter(Boolean);
    return arr.length ? arr : undefined;
  }

  async function handleSubmit() {
    setError(null);
    setBusy(true);
    try {
      const body: CreateJobRequest = {
        title,
        description,
        requirements: requirements || undefined,
        requiredSkills: list(skills),
        jobType,
        location: location || undefined,
        salaryMin: num(salaryMin),
        salaryMax: num(salaryMax),
        atsMinScore: num(atsMinScore),
        assessmentPassScore: num(assessmentPassScore),
        assessmentTimeLimit: num(assessmentTimeLimit),
        interviewTopics: list(interviewTopics),
        interviewDuration: num(interviewDuration),
        interviewNumQuestions: num(interviewNumQuestions),
        deadline: deadline || undefined,
      };

      const { data: job } = await api.post<JobDetail>("/jobs", body);

      if (questions.length > 0) {
        await api.post(`/jobs/${job.id}/questions`, { questions });
      }

      // Publish the freshly created job so it shows up on the public board.
      await api.patch(`/jobs/${job.id}/status`, { status: "ACTIVE" });

      navigate(`/hr/pipeline/${job.id}`);
    } catch (err) {
      setError(apiError(err, "Could not create job"));
    } finally {
      setBusy(false);
    }
  }

  const canAdvanceFrom1 = title.trim() && description.trim();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-bold">Post a job</h1>
      <Steps step={step} />

      {error && (
        <div className="mb-4 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      {step === 1 && (
        <div className="space-y-4">
          <Field label="Job title">
            <Input value={title} onChange={(e) => setTitle(e.target.value)} />
          </Field>
          <Field label="Description">
            <Textarea rows={5} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field label="Requirements (optional)">
            <Textarea rows={3} value={requirements} onChange={(e) => setRequirements(e.target.value)} />
          </Field>
          <Field label="Required skills (comma-separated)">
            <Input value={skills} onChange={(e) => setSkills(e.target.value)} placeholder="React, TypeScript, SQL" />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Job type">
              <Select value={jobType} onValueChange={setJobType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="FULL_TIME">Full time</SelectItem>
                  <SelectItem value="PART_TIME">Part time</SelectItem>
                  <SelectItem value="CONTRACT">Contract</SelectItem>
                  <SelectItem value="INTERNSHIP">Internship</SelectItem>
                  <SelectItem value="REMOTE">Remote</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Location">
              <Input value={location} onChange={(e) => setLocation(e.target.value)} />
            </Field>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Field label="Salary min">
              <Input type="number" value={salaryMin} onChange={(e) => setSalaryMin(e.target.value)} />
            </Field>
            <Field label="Salary max">
              <Input type="number" value={salaryMax} onChange={(e) => setSalaryMax(e.target.value)} />
            </Field>
            <Field label="Deadline">
              <Input type="date" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
            </Field>
          </div>
          <Nav onNext={() => setStep(2)} nextDisabled={!canAdvanceFrom1} />
        </div>
      )}

      {step === 2 && (
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Candidates are auto-screened at each stage using these thresholds.
          </p>
          <Field label="Minimum ATS score to pass resume screen (0–100)">
            <Input type="number" value={atsMinScore} onChange={(e) => setAtsMinScore(e.target.value)} />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Assessment pass score (%)">
              <Input type="number" value={assessmentPassScore} onChange={(e) => setAssessmentPassScore(e.target.value)} />
            </Field>
            <Field label="Assessment time limit (min)">
              <Input type="number" value={assessmentTimeLimit} onChange={(e) => setAssessmentTimeLimit(e.target.value)} />
            </Field>
          </div>
          <Field label="Interview topics (comma-separated)">
            <Input value={interviewTopics} onChange={(e) => setInterviewTopics(e.target.value)} placeholder="System design, Behavioral" />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Interview duration (min)">
              <Input type="number" value={interviewDuration} onChange={(e) => setInterviewDuration(e.target.value)} />
            </Field>
            <Field label="Interview questions">
              <Input type="number" value={interviewNumQuestions} onChange={(e) => setInterviewNumQuestions(e.target.value)} />
            </Field>
          </div>
          <Nav onBack={() => setStep(1)} onNext={() => setStep(3)} />
        </div>
      )}

      {step === 3 && (
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Add assessment questions candidates answer after passing the resume screen.
            You can skip this and add them later.
          </p>
          <QuestionBuilder questions={questions} onChange={setQuestions} />
          <Nav
            onBack={() => setStep(2)}
            onSubmit={handleSubmit}
            submitLabel={busy ? "Publishing…" : "Publish job"}
            submitBusy={busy}
            submitDisabled={busy}
          />
        </div>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      {children}
    </div>
  );
}

function Steps({ step }: { step: Step }) {
  const labels = ["Basics", "Pipeline", "Questions"];
  return (
    <div className="mb-6 flex gap-2">
      {labels.map((label, i) => (
        <div
          key={label}
          className={`flex-1 rounded-lg px-3 py-2 text-center text-sm font-medium transition-colors ${
            step === i + 1
              ? "bg-primary text-primary-foreground shadow-sm shadow-primary/25"
              : step > i + 1
                ? "bg-primary/12 text-primary"
                : "bg-secondary text-muted-foreground"
          }`}
        >
          {i + 1}. {label}
        </div>
      ))}
    </div>
  );
}

function Nav({
  onBack,
  onNext,
  nextDisabled,
  onSubmit,
  submitLabel,
  submitDisabled,
  submitBusy,
}: {
  onBack?: () => void;
  onNext?: () => void;
  nextDisabled?: boolean;
  onSubmit?: () => void;
  submitLabel?: string;
  submitDisabled?: boolean;
  submitBusy?: boolean;
}) {
  return (
    <div className="flex justify-between pt-2">
      {onBack ? (
        <Button variant="outline" onClick={onBack}>
          Back
        </Button>
      ) : (
        <span />
      )}
      {onNext && (
        <Button onClick={onNext} disabled={nextDisabled}>
          Next
        </Button>
      )}
      {onSubmit && (
        <Button variant="gradient" onClick={onSubmit} disabled={submitDisabled}>
          {submitBusy && <Loader2 className="size-4 animate-spin" />}
          {submitLabel}
        </Button>
      )}
    </div>
  );
}
