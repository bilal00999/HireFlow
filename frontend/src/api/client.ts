import axios from "axios";

/**
 * Central axios instance. Base URL points at the Spring backend; every request
 * carries the stored JWT (if any). A 401 clears the session and bounces the
 * user to login, so an expired token can't leave the app in a half-authed state.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1",
});

const TOKEN_KEY = "hireflow.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      setToken(null);
      // Avoid a redirect loop if we're already on an auth page.
      if (!window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);

/** Pulls a human-readable message out of a backend ErrorResponse, if present. */
export function apiError(
  error: unknown,
  fallback = "Something went wrong",
): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message ?? error.message ?? fallback;
  }
  return fallback;
}

/**
 * Builds the interview WebSocket URL for a token. Derives ws/wss + host from the
 * API base URL so it works in dev (localhost:8080) and prod without extra config.
 */
export function interviewSocketUrl(token: string): string {
  const base = api.defaults.baseURL ?? "http://localhost:8080/api/v1";
  // Strip the "/api/v1" suffix — the socket lives at the server root (/ws/...).
  const root = base.replace(/\/api\/v1\/?$/, "");
  const wsRoot = root.replace(/^http/, "ws");
  return `${wsRoot}/ws/interview/${token}`;
}

export default api;
