import { Link, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../api/auth";

/**
 * App shell: top nav that adapts to auth state and role, plus the routed page
 * body. Candidate and HR each see only their own primary links.
 */
export default function Layout() {
  const { user, isAuthenticated, isHr, isCandidate, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <nav className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-lg font-bold text-indigo-600">
            HireFlow
          </Link>

          <div className="flex items-center gap-4 text-sm">
            <Link to="/jobs" className="hover:text-indigo-600">
              Jobs
            </Link>

            {isCandidate && (
              <Link to="/applications" className="hover:text-indigo-600">
                My Applications
              </Link>
            )}

            {isHr && (
              <Link to="/hr/dashboard" className="hover:text-indigo-600">
                Dashboard
              </Link>
            )}

            {isAuthenticated ? (
              <>
                <span className="text-slate-500">{user?.name}</span>
                <button
                  onClick={handleLogout}
                  className="rounded-md bg-slate-100 px-3 py-1.5 font-medium hover:bg-slate-200"
                >
                  Log out
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="hover:text-indigo-600">
                  Log in
                </Link>
                <Link
                  to="/register"
                  className="rounded-md bg-indigo-600 px-3 py-1.5 font-medium text-white hover:bg-indigo-700"
                >
                  Sign up
                </Link>
              </>
            )}
          </div>
        </nav>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
