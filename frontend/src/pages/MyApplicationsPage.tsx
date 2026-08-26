import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import api, { apiError } from "../api/client";
import type { MyApplication } from "../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { StageBadge } from "@/components/StageBadge";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Candidate view of their own applications with the current pipeline stage.
 * A colored badge maps each stage to an at-a-glance status.
 */
export default function MyApplicationsPage() {
  const [apps, setApps] = useState<MyApplication[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<MyApplication[]>("/applications/my")
      .then(({ data }) => setApps(data))
      .catch((err) => setError(apiError(err, "Could not load your applications")));
  }, []);

  if (error) {
    return (
      <div className="rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
        {error}
      </div>
    );
  }

  if (!apps) {
    return (
      <div className="space-y-3">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-24 w-full rounded-2xl" />
        ))}
      </div>
    );
  }

  if (apps.length === 0) {
    return (
      <div className="py-16 text-center text-muted-foreground">
        <p className="mb-4">You haven't applied to any jobs yet.</p>
        <Link to="/jobs" className="font-medium text-primary hover:underline">
          Browse jobs
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold">My Applications</h1>
      <ul className="space-y-3">
        {apps.map((app, i) => (
          <motion.li
            key={app.id}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: Math.min(i * 0.04, 0.3) }}
          >
            <GlassCard className="flex items-center justify-between gap-4 p-4">
              <div className="min-w-0">
                <Link
                  to={`/jobs/${app.job.id}`}
                  className="font-semibold text-primary hover:underline"
                >
                  {app.job.title}
                </Link>
                <p className="text-sm text-muted-foreground">{app.job.company}</p>
                <p className="mt-1 text-xs text-muted-foreground/80">
                  Applied {new Date(app.appliedAt).toLocaleDateString()}
                </p>
              </div>
              <div className="text-right">
                <StageBadge stage={app.stage} />
                {app.stage === "REJECTED" && app.rejectionReason && (
                  <p className="mt-1 text-xs text-muted-foreground/80">
                    {app.rejectionReason.replace(/_/g, " ").toLowerCase()}
                  </p>
                )}
              </div>
            </GlassCard>
          </motion.li>
        ))}
      </ul>
    </div>
  );
}
