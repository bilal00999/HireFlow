import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import api, { apiError } from "../api/client";
import type { JobSearchResult, JobSummary } from "../api/types";

/**
 * Public job board: keyword/type/location search over ACTIVE jobs, paginated.
 * The backend returns only ACTIVE jobs, so no client-side status filtering.
 */
export default function JobListPage() {
  const [jobs, setJobs] = useState<JobSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Committed search terms (what we actually query with).
  const [keyword, setKeyword] = useState("");
  const [location, setLocation] = useState("");
  // Draft input values, applied on submit.
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
    <div>
      <h1 className="mb-6 text-2xl font-bold">Open roles</h1>

      <form onSubmit={handleSearch} className="mb-6 flex flex-wrap gap-3">
        <input
          placeholder="Search title or keyword"
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          className="flex-1 rounded-md border border-slate-300 px-3 py-2"
        />
        <input
          placeholder="Location"
          value={locationInput}
          onChange={(e) => setLocationInput(e.target.value)}
          className="w-48 rounded-md border border-slate-300 px-3 py-2"
        />
        <button
          type="submit"
          className="rounded-md bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700"
        >
          Search
        </button>
      </form>

      {loading && <p className="text-slate-500">Loading jobs…</p>}
      {error && (
        <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}

      {!loading && !error && jobs.length === 0 && (
        <p className="text-slate-500">No jobs match your search.</p>
      )}

      <ul className="space-y-3">
        {jobs.map((job) => (
          <li key={job.id}>
            <Link
              to={`/jobs/${job.id}`}
              className="block rounded-lg border border-slate-200 bg-white p-4 hover:border-indigo-300 hover:shadow-sm"
            >
              <div className="flex items-center justify-between">
                <h2 className="font-semibold text-slate-900">{job.title}</h2>
                <span className="text-sm text-slate-500">{job.company}</span>
              </div>
              <div className="mt-1 flex flex-wrap gap-x-4 text-sm text-slate-600">
                {job.jobType && <span>{job.jobType.replace("_", " ")}</span>}
                {job.location && <span>{job.location}</span>}
                {job.salaryMin != null && job.salaryMax != null && (
                  <span>
                    {job.currency ?? "USD"} {job.salaryMin.toLocaleString()}–
                    {job.salaryMax.toLocaleString()}
                  </span>
                )}
              </div>
            </Link>
          </li>
        ))}
      </ul>

      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-md bg-slate-100 px-3 py-1.5 text-sm font-medium disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-slate-600">
            Page {page + 1} of {totalPages}
          </span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-md bg-slate-100 px-3 py-1.5 text-sm font-medium disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
