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

    logout: () => {
      setToken(null);
      persist(null);
      set({ user: null, isAuthenticated: false, isHr: false, isCandidate: false });
    },
  };
});
