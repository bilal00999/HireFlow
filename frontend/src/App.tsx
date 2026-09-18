import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Layout from "./components/Layout";
import ProtectedRoute from "./components/ProtectedRoute";
import { Toaster } from "@/components/ui/sonner";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import JobListPage from "./pages/JobListPage";
import JobDetailPage from "./pages/JobDetailPage";
import MyApplicationsPage from "./pages/MyApplicationsPage";
import HrDashboardPage from "./pages/hr/HrDashboardPage";
import PipelinePage from "./pages/hr/PipelinePage";
import CandidateDetailPage from "./pages/hr/CandidateDetailPage";
import CreateJobPage from "./pages/hr/CreateJobPage";
import AssessmentPage from "./pages/AssessmentPage";
import InterviewPage from "./pages/InterviewPage";
import ProfilePage from "./pages/ProfilePage";

/**
 * App routes. Public: job browsing + auth. Candidate-only: my applications.
 * HR-only: dashboard, pipeline, create job. The token-gated assessment and
 * interview pages sit outside the Layout — candidates reach them from an
 * emailed link and need no app chrome or login.
 */
function App() {
  return (
    <BrowserRouter>
      <Toaster />
      <Routes>
        {/* Standalone token-gated flows (no nav shell, no auth) */}
        <Route path="/assessment/:token" element={<AssessmentPage />} />
        <Route path="/interview/:token" element={<InterviewPage />} />

        <Route element={<Layout />}>
          <Route index element={<JobListPage />} />
          <Route path="jobs" element={<JobListPage />} />
          <Route path="jobs/:id" element={<JobDetailPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />

          <Route
            path="applications"
            element={
              <ProtectedRoute role="CANDIDATE">
                <MyApplicationsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="profile"
            element={
              <ProtectedRoute role="CANDIDATE">
                <ProfilePage />
              </ProtectedRoute>
            }
          />

          <Route
            path="hr/dashboard"
            element={
              <ProtectedRoute role="HR">
                <HrDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="hr/jobs/new"
            element={
              <ProtectedRoute role="HR">
                <CreateJobPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="hr/pipeline/:jobId"
            element={
              <ProtectedRoute role="HR">
                <PipelinePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="hr/candidate/:applicationId"
            element={
              <ProtectedRoute role="HR">
                <CandidateDetailPage />
              </ProtectedRoute>
            }
          />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
