// Shared API types mirroring the backend DTOs (com.example.demo.*.dto).

export type Role = "CANDIDATE" | "HR";

export interface AuthResponse {
  token: string;
  id: string;
  email: string;
  name: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterCandidateRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

export interface RegisterCompanyRequest {
  email: string;
  password: string;
  companyName: string;
  industry?: string;
}

export interface JobSummary {
  id: string;
  title: string;
  company: string;
  jobType: string | null;
  location: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string | null;
  deadline: string | null;
  status: string;
}

export interface JobDetail {
  id: string;
  title: string;
  company: string;
  description: string;
  requirements: string | null;
  requiredSkills: string[];
  jobType: string | null;
  location: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string | null;
  deadline: string | null;
  status: string;
}

export interface JobSearchResult {
  content: JobSummary[];
  totalPages: number;
  totalElements: number;
  page: number;
}

export interface MyApplication {
  id: string;
  job: { id: string; title: string; company: string };
  stage: string;
  rejectionReason: string | null;
  appliedAt: string;
}

export interface ApplyResponse {
  applicationId: string;
  stage: string;
  message: string;
}
