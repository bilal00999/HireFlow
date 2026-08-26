import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Search, MapPin, Briefcase, Building2, AlertCircle } from "lucide-react";

import api, { apiError } from "../api/client";
import type { JobSearchResult, JobSummary } from "../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { GradientText } from "@/components/ui/gradient-text";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Public job board: keyword/type/location search over ACTIVE jobs, paginated.
 * The backend returns only ACTIVE jobs, so no client-side status filtering.
 * Search + pagination logic is unchanged; this is a visual reskin.
 */
export default function JobListPage() {
  const [jobs, setJobs] = useState<JobSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [keyword, setKeyword] = useState("");
  const [location, setLocation] = useState("");
  const [keywordInput, setKeywordInput] = useState("");
  const [locationInput, setLocationInput] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api
      .get<JobSearchResult>("/jobs", { params: { keyword, location, page, size: 10 } })
      .then(({ data }) => {
        if (cancelled) return;
        setJobs(data.content);
        setTotalPages(data.totalPages);
      })
      .catch((err) => !cancelled && setError(apiError(err, "Could not load jobs")))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [keyword, location, page]);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setPage(0);
    setKeyword(keywordInput.trim());
    setLocation(locationInput.trim());
  }

  return (
    <div className="mx-auto max-w-4xl">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="mb-8 text-center"
      >
        <h1 className="text-3xl font-bold sm:text-4xl">
          Find your next <GradientText>role</GradientText>
        </h1>
        <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
          Browse open positions and apply in minutes.
        </p>
      </motion.div>

      <GlassCard hover={false} className="mb-8 p-4">
        <form onSubmit={handleSearch} className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search title or keyword"
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              className="pl-9"
            />
          </div>
          <div className="relative sm:w-52">
            <MapPin className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Location"
              value={locationInput}
              onChange={(e) => setLocationInput(e.target.value)}
              className="pl-9"
            />
          </div>
          <Button type="submit" variant="gradient">
            <Search className="size-4" />
            Search
          </Button>
        </form>
      </GlassCard>

      {loading && (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-24 w-full rounded-xl" />
          ))}
        </div>
      )}

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-red-500/25 bg-red-500/10 px-3 py-2 text-sm text-red-700">
          <AlertCircle className="size-4 shrink-0" />
          {error}
        </div>
      )}

      {!loading && !error && jobs.length === 0 && (
        <GlassCard hover={false} className="py-16 text-center">
          <Briefcase className="mx-auto mb-3 size-8 text-muted-foreground" />
          <p className="text-muted-foreground">No jobs match your search.</p>
        </GlassCard>
      )}

      <ul className="space-y-3">
        {jobs.map((job, i) => (
          <motion.li
            key={job.id}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: Math.min(i * 0.04, 0.3) }}
          >
            <Link to={`/jobs/${job.id}`} className="block">
              <GlassCard className="p-5">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <h2 className="font-semibold">{job.title}</h2>
                    <p className="mt-0.5 flex items-center gap-1.5 text-sm text-muted-foreground">
                      <Building2 className="size-3.5" />
                      {job.company}
                    </p>
                  </div>
                  {job.jobType && (
                    <Badge variant="info">{job.jobType.replace("_", " ")}</Badge>
                  )}
                </div>
                <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
                  {job.location && (
                    <span className="flex items-center gap-1">
                      <MapPin className="size-3.5" />
                      {job.location}
                    </span>
                  )}
                  {job.salaryMin != null && job.salaryMax != null && (
                    <span>
                      {job.currency ?? "USD"} {job.salaryMin.toLocaleString()}–
                      {job.salaryMax.toLocaleString()}
                    </span>
                  )}
                </div>
              </GlassCard>
            </Link>
          </motion.li>
        ))}
      </ul>

      {totalPages > 1 && (
        <div className="mt-8 flex items-center justify-center gap-4">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
