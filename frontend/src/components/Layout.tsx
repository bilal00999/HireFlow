import { useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { AnimatePresence } from "framer-motion";
import {
  Briefcase,
  LayoutDashboard,
  FileText,
  PlusCircle,
  LogOut,
  LogIn,
  UserPlus,
  User,
  Menu,
  type LucideIcon,
} from "lucide-react";

import { useAuth } from "../api/auth";
import { cn } from "@/lib/utils";
import { Aurora } from "@/components/ui/aurora";
import { GradientText } from "@/components/ui/gradient-text";
import { PageTransition } from "@/components/ui/page-transition";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Sheet, SheetContent, SheetTrigger, SheetClose } from "@/components/ui/sheet";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
}

/**
 * App shell: a glass sidebar (desktop) / drawer (mobile) with role-adaptive
 * navigation over an animated aurora backdrop, plus the routed page body
 * wrapped in a route transition.
 */
export default function Layout() {
  const { user, isAuthenticated, isHr, isCandidate, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);

  function handleLogout() {
    logout();
    navigate("/login");
  }

  const nav: NavItem[] = [{ to: "/jobs", label: "Jobs", icon: Briefcase }];
  if (isCandidate)
    nav.push({ to: "/applications", label: "My Applications", icon: FileText });
  if (isHr)
    nav.push(
      { to: "/hr/dashboard", label: "Dashboard", icon: LayoutDashboard },
      { to: "/hr/jobs/new", label: "Post a job", icon: PlusCircle },
    );

  const initials = (user?.name ?? "?")
    .split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  const brand = (
    <Link to="/" className="flex items-center gap-2 px-3 py-1">
      <div className="grid size-8 place-items-center rounded-lg bg-gradient-to-br from-blue-500 to-indigo-500 font-bold text-white">
        H
      </div>
      <GradientText className="text-lg font-bold">HireFlow</GradientText>
    </Link>
  );

  const navLinks = (onNavigate?: () => void) =>
    nav.map(({ to, label, icon: Icon }) => (
      <NavLink
        key={to}
        to={to}
        onClick={onNavigate}
        className={({ isActive }) =>
          cn(
            "flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-colors",
            isActive
              ? "bg-primary text-primary-foreground shadow-sm shadow-primary/25"
              : "text-muted-foreground hover:bg-accent hover:text-accent-foreground",
          )
        }
      >
        <Icon className="size-4" />
        {label}
      </NavLink>
    ));

  const userBlock = (
    <div className="border-t border-border p-3">
      {isAuthenticated ? (
        <DropdownMenu>
          <DropdownMenuTrigger className="flex w-full items-center gap-3 rounded-xl px-2 py-2 text-left transition-colors hover:bg-accent">
            <Avatar className="size-8">
              <AvatarFallback className="text-xs">{initials}</AvatarFallback>
            </Avatar>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{user?.name}</p>
              <p className="truncate text-xs text-muted-foreground">{user?.role}</p>
            </div>
          </DropdownMenuTrigger>
          <DropdownMenuContent side="top" className="w-48">
            <DropdownMenuLabel className="truncate">{user?.email}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            {isCandidate && (
              <DropdownMenuItem
                onClick={() => {
                  setMobileOpen(false);
                  navigate("/profile");
                }}
              >
                <User />
                Profile
              </DropdownMenuItem>
            )}
            <DropdownMenuItem onClick={handleLogout}>
              <LogOut />
              Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      ) : (
        <div className="flex flex-col gap-2">
          <Link
            to="/login"
            onClick={() => setMobileOpen(false)}
            className="flex items-center gap-2 rounded-xl px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          >
            <LogIn className="size-4" />
            Log in
          </Link>
          <Link
            to="/register"
            onClick={() => setMobileOpen(false)}
            className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-500 to-indigo-500 px-3 py-2 text-sm font-medium text-white shadow-sm shadow-primary/25 transition-opacity hover:opacity-90"
          >
            <UserPlus className="size-4" />
            Sign up
          </Link>
        </div>
      )}
    </div>
  );

  return (
    <div className="min-h-screen bg-background text-foreground">
      <Aurora />

      <div className="flex min-h-screen">
        {/* Desktop sidebar */}
        <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-border bg-sidebar md:flex">
          <div className="py-4">{brand}</div>
          <nav className="flex flex-1 flex-col gap-1 px-3">{navLinks()}</nav>
          {userBlock}
        </aside>

        <main className="flex-1 overflow-x-hidden">
          {/* Mobile top bar */}
          <div className="flex items-center justify-between border-b border-border bg-sidebar px-4 py-3 md:hidden">
            {brand}
            <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
              <SheetTrigger className="rounded-lg p-2 hover:bg-accent">
                <Menu className="size-5" />
              </SheetTrigger>
              <SheetContent side="left" className="w-64 p-0">
                <div className="flex h-full flex-col pt-6">
                  <SheetClose className="hidden" />
                  <nav className="flex flex-1 flex-col gap-1 px-3">
                    {navLinks(() => setMobileOpen(false))}
                  </nav>
                  {userBlock}
                </div>
              </SheetContent>
            </Sheet>
          </div>

          <div className="mx-auto max-w-6xl px-4 py-8 sm:px-8 sm:py-10">
            <AnimatePresence mode="wait">
              <PageTransition key={location.pathname}>
                <Outlet />
              </PageTransition>
            </AnimatePresence>
          </div>
        </main>
      </div>
    </div>
  );
}
