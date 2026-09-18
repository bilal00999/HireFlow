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

// --- Candidate profile (com.example.demo.user.dto + file.dto) ---

/** Public profile image; `url` is a Cloudinary secure URL, safe in <img>. */
export interface ProfileImageResponse {
  url: string;
  fileName: string;
  contentType: string;
  size: number | null;
  updatedAt: string;
}

/** Resume metadata. Deliberately no direct storage URL — download via the API. */
export interface ResumeResponse {
  fileName: string;
  contentType: string;
  size: number | null;
  updatedAt: string;
  downloadUrl: string;
}

/** Full candidate profile hydrated in one call; file fields are null when unset. */
export interface MeResponse {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  linkedinUrl: string | null;
  role: Role;
  profileImage: ProfileImageResponse | null;
  resume: ResumeResponse | null;
}

export interface UpdateProfileRequest {
  fullName: string;
  email: string;
  phone?: string;
  linkedinUrl?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
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

// --- Assessment (token-gated candidate flow) ---

export interface VerifyTokenResponse {
  valid: boolean;
  reason?: string;
  jobTitle?: string;
  questionCount?: number;
  timeLimit?: number;
}

export interface CandidateQuestion {
  id: string;
  questionText: string;
  questionType: "MCQ" | "TEXT";
  options: string[];
  maxScore: number | null;
}

export interface AssessmentQuestions {
  jobTitle: string;
  timeLimit: number;
  questions: CandidateQuestion[];
}

export interface AnswerInput {
  questionId: string;
  selectedOption?: number;
  answerText?: string;
}

export interface SubmitAssessmentResponse {
  submitted: boolean;
  message: string;
}

// --- Interview ---

export interface VerifyInterviewResponse {
  valid: boolean;
  reason?: string;
  jobTitle?: string;
  durationMinutes?: number;
}

// --- HR dashboard + pipeline ---

export interface DashboardStats {
  activeJobs: number;
  totalApplications: number;
  inAssessment: number;
  inInterview: number;
  readyForReview: number;
}

export interface Applicant {
  id: string;
  candidateName: string;
  candidateEmail: string;
  resumeUrl: string | null;
  coverLetter: string | null;
  stage: string;
  rejectionReason: string | null;
  appliedAt: string;
}

export interface Pipeline {
  jobId: string;
  jobTitle: string;
  stages: Record<string, number>;
  candidates: Applicant[];
}

// --- HR create/manage jobs ---

export interface QuestionInput {
  questionText: string;
  questionType: "MCQ" | "TEXT";
  options?: string[];
  correctOption?: number;
  maxScore?: number;
}

export interface CreateJobRequest {
  title: string;
  description: string;
  requirements?: string;
  requiredSkills?: string[];
  jobType?: string;
  location?: string;
  salaryMin?: number;
  salaryMax?: number;
  atsMinScore?: number;
  assessmentPassScore?: number;
  assessmentTimeLimit?: number;
  interviewTopics?: string[];
  interviewDuration?: number;
  interviewNumQuestions?: number;
  deadline?: string;
}

// --- HR candidate detail (consolidated scorecard) ---

export interface TranscriptMessage {
  role: "ai" | "candidate";
  content: string;
  order: number;
}

export interface AtsSection {
  score: number;
  passed: boolean;
  summary: string | null;
  matchedSkills: string[];
  missingSkills: string[];
}

export interface InterviewSection {
  score: number | null;
  passed: boolean | null;
  recommendation: string | null;
  summary: string | null;
  strengths: string[] | null;
  weaknesses: string[] | null;
}

export interface CandidateDetail {
  applicationId: string;
  candidateName: string;
  candidateEmail: string;
  jobId: string;
  jobTitle: string;
  stage: string;
  rejectionReason: string | null;
  resumeUrl: string | null;
  coverLetter: string | null;
  ats: AtsSection | null;
  assessmentScore: number | null;
  interview: InterviewSection | null;
  transcript: TranscriptMessage[];
}

export type DecisionKind = "HIRE" | "REJECT";
