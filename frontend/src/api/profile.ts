import api from "./client";
import type {
  ChangePasswordRequest,
  MeResponse,
  ProfileImageResponse,
  ResumeResponse,
  UpdateProfileRequest,
} from "./types";

/**
 * Candidate profile API. Ownership is always resolved from the JWT on the
 * server, so none of these calls take (or accept) a userId.
 */

/** Full profile in one call: identity + image/resume metadata (files may be null). */
export function getMe(): Promise<MeResponse> {
  return api.get<MeResponse>("/users/me").then((r) => r.data);
}

/** Update name, email, phone and LinkedIn URL. */
export function updateProfile(req: UpdateProfileRequest): Promise<MeResponse> {
  return api.patch<MeResponse>("/users/me", req).then((r) => r.data);
}

/** Change password; requires the current password (verified server-side). */
export function changePassword(req: ChangePasswordRequest): Promise<void> {
  return api.patch("/users/me/password", req).then(() => undefined);
}

/** Upload or replace the profile picture (JPG/PNG/WEBP, ≤ 2 MB). */
export function uploadProfileImage(file: File): Promise<ProfileImageResponse> {
  const form = new FormData();
  form.append("file", file);
  // Let axios set the multipart boundary; the interceptor still adds the token.
  return api
    .post<ProfileImageResponse>("/users/profile-image", form)
    .then((r) => r.data);
}

export function deleteProfileImage(): Promise<void> {
  return api.delete("/users/profile-image").then(() => undefined);
}

/** Upload or replace the resume (PDF/DOCX, ≤ 5 MB). */
export function uploadResume(file: File): Promise<ResumeResponse> {
  const form = new FormData();
  form.append("file", file);
  return api.post<ResumeResponse>("/resumes", form).then((r) => r.data);
}

export function deleteResume(): Promise<void> {
  return api.delete("/resumes").then(() => undefined);
}

/**
 * Fetches a short-lived signed URL for the current resume. We can't put the
 * Bearer token on a plain link, so we ask the API (token attached by the
 * interceptor) for the signed URL and open that directly — no CORS issue,
 * since opening it is a top-level navigation, not an XHR.
 */
export function resumeDownloadUrl(): Promise<string> {
  return api
    .get<{ url: string }>("/resumes/download-url")
    .then((r) => r.data.url);
}
