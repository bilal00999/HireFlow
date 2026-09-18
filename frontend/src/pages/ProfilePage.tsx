import {
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
} from "react";
import {
  Camera,
  Download,
  FileText,
  Link,
  Loader2,
  Lock,
  Mail,
  Phone,
  ShieldCheck,
  Trash2,
  Upload,
  User as UserIcon,
} from "lucide-react";
import { toast } from "sonner";

import { useAuth } from "../api/auth";
import { apiError } from "../api/client";
import * as profileApi from "../api/profile";
import type { ProfileImageResponse, ResumeResponse } from "../api/types";
import { GlassCard } from "@/components/ui/glass-card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

// Mirror the server-side limits (UserFileService) so we can fail fast client-side.
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_RESUME_BYTES = 5 * 1024 * 1024;
const RESUME_TYPES = [
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
];

interface AccountForm {
  fullName: string;
  email: string;
  phone: string;
  linkedinUrl: string;
}

const EMPTY_ACCOUNT: AccountForm = {
  fullName: "",
  email: "",
  phone: "",
  linkedinUrl: "",
};

function formatBytes(bytes?: number | null): string {
  if (!bytes) return "";
  const mb = bytes / (1024 * 1024);
  return mb >= 1
    ? `${mb.toFixed(1)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function initialsOf(name: string): string {
  return (
    name
      .split(" ")
      .map((p) => p[0])
      .filter(Boolean)
      .slice(0, 2)
      .join("")
      .toUpperCase() || "?"
  );
}

/**
 * Candidate profile: edit account details (name/email/phone/LinkedIn), manage
 * the Cloudinary profile picture and resume, and change password. Everything
 * loads from a single GET /users/me. This profile-level resume (/api/v1/resumes)
 * is independent of the per-application resume submitted when applying to a job.
 */
export default function ProfilePage() {
  const { user, updateUser } = useAuth();

  const [loading, setLoading] = useState(true);

  // Account details
  const [account, setAccount] = useState<AccountForm>(EMPTY_ACCOUNT);
  const [baseline, setBaseline] = useState<AccountForm>(EMPTY_ACCOUNT);
  const [savingAccount, setSavingAccount] = useState(false);

  // Profile picture
  const [image, setImage] = useState<ProfileImageResponse | null>(null);
  const [imageBusy, setImageBusy] = useState(false);
  const imageInput = useRef<HTMLInputElement>(null);

  // Resume
  const [resume, setResume] = useState<ResumeResponse | null>(null);
  const [resumeBusy, setResumeBusy] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const resumeInput = useRef<HTMLInputElement>(null);

  // Password
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [savingPassword, setSavingPassword] = useState(false);

  useEffect(() => {
    let active = true;
    profileApi
      .getMe()
      .then((me) => {
        if (!active) return;
        const form: AccountForm = {
          fullName: me.fullName ?? "",
          email: me.email ?? "",
          phone: me.phone ?? "",
          linkedinUrl: me.linkedinUrl ?? "",
        };
        setAccount(form);
        setBaseline(form);
        setImage(me.profileImage);
        setResume(me.resume);
      })
      .catch((err) => toast.error(apiError(err, "Could not load your profile")))
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const accountDirty =
    account.fullName !== baseline.fullName ||
    account.email !== baseline.email ||
    account.phone !== baseline.phone ||
    account.linkedinUrl !== baseline.linkedinUrl;

  const displayName = account.fullName || user?.name || "";
  const initials = initialsOf(displayName);

  // ---------------------------------------------------------------- account

  async function saveAccount(e: FormEvent) {
    e.preventDefault();
    if (!account.fullName.trim()) {
      toast.error("Full name is required");
      return;
    }
    setSavingAccount(true);
    try {
      const me = await profileApi.updateProfile({
        fullName: account.fullName.trim(),
        email: account.email.trim(),
        phone: account.phone.trim(),
        linkedinUrl: account.linkedinUrl.trim(),
      });
      const form: AccountForm = {
        fullName: me.fullName ?? "",
        email: me.email ?? "",
        phone: me.phone ?? "",
        linkedinUrl: me.linkedinUrl ?? "",
      };
      setAccount(form);
      setBaseline(form);
      // Reflect name/email in the session so the sidebar updates immediately.
      updateUser({ name: me.fullName, email: me.email });
      toast.success("Profile updated");
    } catch (err) {
      toast.error(apiError(err, "Could not update your profile"));
    } finally {
      setSavingAccount(false);
    }
  }

  // ---------------------------------------------------------------- picture

  async function onPickImage(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow re-picking the same file after an error
    if (!file) return;
    if (!IMAGE_TYPES.includes(file.type)) {
      toast.error("Profile picture must be a JPG, PNG or WEBP image");
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      toast.error("Profile picture must be 2 MB or smaller");
      return;
    }
    setImageBusy(true);
    try {
      setImage(await profileApi.uploadProfileImage(file));
      toast.success("Profile picture updated");
    } catch (err) {
      toast.error(apiError(err, "Could not upload your profile picture"));
    } finally {
      setImageBusy(false);
    }
  }

  async function removeImage() {
    setImageBusy(true);
    try {
      await profileApi.deleteProfileImage();
      setImage(null);
      toast.success("Profile picture removed");
    } catch (err) {
      toast.error(apiError(err, "Could not remove your profile picture"));
    } finally {
      setImageBusy(false);
    }
  }

  // ---------------------------------------------------------------- resume

  async function onPickResume(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    const ext = file.name.split(".").pop()?.toLowerCase();
    const okType =
      RESUME_TYPES.includes(file.type) || ext === "pdf" || ext === "docx";
    if (!okType) {
      toast.error("Resume must be a PDF or DOCX file");
      return;
    }
    if (file.size > MAX_RESUME_BYTES) {
      toast.error("Resume must be 5 MB or smaller");
      return;
    }
    setResumeBusy(true);
    try {
      setResume(await profileApi.uploadResume(file));
      toast.success("Resume uploaded");
    } catch (err) {
      toast.error(apiError(err, "Could not upload your resume"));
    } finally {
      setResumeBusy(false);
    }
  }

  async function downloadResume() {
    setDownloading(true);
    try {
      const url = await profileApi.resumeDownloadUrl();
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(apiError(err, "Could not open your resume"));
    } finally {
      setDownloading(false);
    }
  }

  async function removeResume() {
    setResumeBusy(true);
    try {
      await profileApi.deleteResume();
      setResume(null);
      toast.success("Resume removed");
    } catch (err) {
      toast.error(apiError(err, "Could not remove your resume"));
    } finally {
      setResumeBusy(false);
    }
  }

  // ---------------------------------------------------------------- password

  async function savePassword(e: FormEvent) {
    e.preventDefault();
    if (newPassword.length < 6) {
      toast.error("New password must be at least 6 characters");
      return;
    }
    if (newPassword !== confirmPassword) {
      toast.error("New passwords do not match");
      return;
    }
    setSavingPassword(true);
    try {
      await profileApi.changePassword({ currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      toast.success("Password changed");
    } catch (err) {
      toast.error(apiError(err, "Could not change your password"));
    } finally {
      setSavingPassword(false);
    }
  }

  // ---------------------------------------------------------------- render

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <Skeleton className="h-8 w-40 rounded-lg" />
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-40 w-full rounded-2xl" />
        ))}
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-1 text-2xl font-bold">Profile</h1>
      <p className="mb-6 text-sm text-muted-foreground">
        Manage your account details, profile picture and resume.
      </p>

      {/* Profile picture */}
      <GlassCard hover={false} className="mb-4 p-6">
        <div className="flex items-center gap-5">
          <Avatar className="size-20">
            {image?.url && (
              <AvatarImage src={image.url} alt={displayName || "avatar"} />
            )}
            <AvatarFallback className="text-xl">{initials}</AvatarFallback>
          </Avatar>

          <div className="flex-1">
            <h2 className="font-semibold">Profile picture</h2>
            <p className="text-sm text-muted-foreground">
              JPG, PNG or WEBP, up to 2&nbsp;MB.
            </p>

            <div className="mt-3 flex flex-wrap gap-2">
              <input
                ref={imageInput}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="hidden"
                onChange={onPickImage}
              />
              <Button
                variant="outline"
                size="sm"
                disabled={imageBusy}
                onClick={() => imageInput.current?.click()}
              >
                {imageBusy ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Camera className="size-4" />
                )}
                {image ? "Replace" : "Upload"}
              </Button>
              {image && (
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={imageBusy}
                  onClick={removeImage}
                >
                  <Trash2 className="size-4" />
                  Remove
                </Button>
              )}
            </div>
          </div>
        </div>
      </GlassCard>

      {/* Account details */}
      <GlassCard hover={false} className="mb-4 p-6">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="font-semibold">Account details</h2>
          <Badge variant="info" className="gap-1">
            <ShieldCheck className="size-3.5" />
            {user?.role}
          </Badge>
        </div>

        <form onSubmit={saveAccount} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <IconField id="fullName" label="Full name" icon={UserIcon}>
              <Input
                id="fullName"
                required
                value={account.fullName}
                onChange={(e) =>
                  setAccount((a) => ({ ...a, fullName: e.target.value }))
                }
                className="pl-9"
              />
            </IconField>
            <IconField id="email" label="Email" icon={Mail}>
              <Input
                id="email"
                type="email"
                required
                value={account.email}
                onChange={(e) =>
                  setAccount((a) => ({ ...a, email: e.target.value }))
                }
                className="pl-9"
              />
            </IconField>
            <IconField id="phone" label="Phone (optional)" icon={Phone}>
              <Input
                id="phone"
                value={account.phone}
                onChange={(e) =>
                  setAccount((a) => ({ ...a, phone: e.target.value }))
                }
                className="pl-9"
              />
            </IconField>
            <IconField
              id="linkedinUrl"
              label="LinkedIn URL (optional)"
              icon={Link}
            >
              <Input
                id="linkedinUrl"
                type="url"
                value={account.linkedinUrl}
                onChange={(e) =>
                  setAccount((a) => ({ ...a, linkedinUrl: e.target.value }))
                }
                className="pl-9"
                placeholder="https://linkedin.com/in/…"
              />
            </IconField>
          </div>

          <div className="flex justify-end">
            <Button
              type="submit"
              variant="gradient"
              disabled={savingAccount || !accountDirty}
            >
              {savingAccount && <Loader2 className="size-4 animate-spin" />}
              {savingAccount ? "Saving…" : "Save changes"}
            </Button>
          </div>
        </form>
      </GlassCard>

      {/* Resume */}
      <GlassCard hover={false} className="mb-4 p-6">
        <h2 className="mb-1 font-semibold">Resume</h2>
        <p className="mb-4 text-sm text-muted-foreground">
          PDF or DOCX, up to 5&nbsp;MB.
        </p>

        {resume ? (
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-border bg-secondary/40 p-4">
            <div className="flex min-w-0 items-center gap-3">
              <div className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
                <FileText className="size-5" />
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">
                  {resume.fileName}
                </p>
                <p className="text-xs text-muted-foreground">
                  {formatBytes(resume.size)}
                  {resume.updatedAt &&
                    ` · Updated ${new Date(resume.updatedAt).toLocaleDateString()}`}
                </p>
              </div>
            </div>
            <div className="flex shrink-0 gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={downloading}
                onClick={downloadResume}
              >
                {downloading ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Download className="size-4" />
                )}
                Download
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={resumeBusy}
                onClick={removeResume}
              >
                <Trash2 className="size-4" />
              </Button>
            </div>
          </div>
        ) : (
          <p className="rounded-xl border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
            No resume uploaded yet.
          </p>
        )}

        <input
          ref={resumeInput}
          type="file"
          accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          className="hidden"
          onChange={onPickResume}
        />
        <Button
          variant="gradient"
          className="mt-4"
          disabled={resumeBusy}
          onClick={() => resumeInput.current?.click()}
        >
          {resumeBusy ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Upload className="size-4" />
          )}
          {resume ? "Replace resume" : "Upload resume"}
        </Button>
      </GlassCard>

      {/* Password */}
      <GlassCard hover={false} className="p-6">
        <h2 className="mb-4 font-semibold">Change password</h2>
        <form onSubmit={savePassword} className="space-y-4">
          <IconField id="currentPassword" label="Current password" icon={Lock}>
            <Input
              id="currentPassword"
              type="password"
              required
              autoComplete="current-password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="pl-9"
            />
          </IconField>
          <div className="grid gap-4 sm:grid-cols-2">
            <IconField id="newPassword" label="New password" icon={Lock}>
              <Input
                id="newPassword"
                type="password"
                required
                minLength={6}
                autoComplete="new-password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="pl-9"
                placeholder="At least 6 characters"
              />
            </IconField>
            <IconField
              id="confirmPassword"
              label="Confirm new password"
              icon={Lock}
            >
              <Input
                id="confirmPassword"
                type="password"
                required
                minLength={6}
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="pl-9"
              />
            </IconField>
          </div>
          <div className="flex justify-end">
            <Button
              type="submit"
              variant="gradient"
              disabled={
                savingPassword ||
                !currentPassword ||
                !newPassword ||
                !confirmPassword
              }
            >
              {savingPassword && <Loader2 className="size-4 animate-spin" />}
              {savingPassword ? "Updating…" : "Update password"}
            </Button>
          </div>
        </form>
      </GlassCard>
    </div>
  );
}

function IconField({
  id,
  label,
  icon: Icon,
  children,
}: {
  id: string;
  label: string;
  icon: typeof Mail;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Icon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        {children}
      </div>
    </div>
  );
}
