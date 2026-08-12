import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

/**
 * Single source of truth for pipeline-stage styling. Maps each application stage
 * to a Badge variant so MyApplications, Pipeline, and CandidateDetail stay in
 * sync. Unknown stages fall back to muted.
 */
const STAGE_VARIANT: Record<
  string,
  "muted" | "info" | "warning" | "purple" | "success" | "destructive"
> = {
  APPLIED: "muted",
  ATS_REVIEW: "info",
  ASSESSMENT: "warning",
  INTERVIEW: "purple",
  FINAL: "info",
  HIRED: "success",
  REJECTED: "destructive",
  MANUAL_REVIEW: "warning",
};

export function StageBadge({
  stage,
  className,
}: {
  stage: string;
  className?: string;
}) {
  const variant = STAGE_VARIANT[stage] ?? "muted";
  return (
    <Badge variant={variant} className={cn(className)}>
      {stage.replace(/_/g, " ").toLowerCase()}
    </Badge>
  );
}
