import { create } from "zustand";
import api, { setToken, getToken } from "./client";
import type {
  AuthResponse,
  LoginRequest,
  RegisterCandidateRequest,
  RegisterCompanyRequest,
} from "./types";

const USER_KEY = "hireflow.user";

function loadUser(): AuthResponse | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as AuthResponse) : null;
}

function persist(user: AuthResponse | null) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  else localStorage.removeItem(USER_KEY);
}

interface AuthState {
  user: AuthResponse | null;
  isAuthenticated: boolean;
  isHr: boolean;
  isCandidate: boolean;
  login: (req: LoginRequest) => Promise<AuthResponse>;
  registerCandidate: (req: RegisterCandidateRequest) => Promise<AuthResponse>;
  registerCompany: (req: RegisterCompanyRequest) => Promise<AuthResponse>;
  updateUser: (partial: Partial<AuthResponse>) => void;
  logout: () => void;
}

function apply(user: AuthResponse) {
  setToken(user.token);
  persist(user);
  return user;
}

export const useAuth = create<AuthState>((set) => {
  const initial = getToken() ? loadUser() : null;

  return {
    user: initial,
    isAuthenticated: !!initial,
    isHr: initial?.role === "HR",
    isCandidate: initial?.role === "CANDIDATE",

    login: async (req) => {
      const { data } = await api.post<AuthResponse>("/auth/login", req);
      apply(data);
      set({ user: data, isAuthenticated: true, isHr: data.role === "HR", isCandidate: data.role === "CANDIDATE" });
      return data;
    },

    registerCandidate: async (req) => {
      const { data } = await api.post<AuthResponse>("/auth/register/candidate", req);
      apply(data);
      set({ user: data, isAuthenticated: true, isHr: false, isCandidate: true });
      return data;
    },

    registerCompany: async (req) => {
      const { data } = await api.post<AuthResponse>("/auth/register/company", req);
      apply(data);
      set({ user: data, isAuthenticated: true, isHr: true, isCandidate: false });
      return data;
    },

    // Merge server-confirmed profile changes (e.g. name/email) into the stored
    // session so the sidebar and guards reflect them without a re-login. Keeps
    // the existing token; a no-op if there's no active user.
    updateUser: (partial) => {
      set((state) => {
        if (!state.user) return state;
        const next = { ...state.user, ...partial };
        persist(next);
        return {
          user: next,
          isHr: next.role === "HR",
          isCandidate: next.role === "CANDIDATE",
        };
      });
    },

    logout: () => {
      setToken(null);
      persist(null);
      set({ user: null, isAuthenticated: false, isHr: false, isCandidate: false });
    },
  };
});
