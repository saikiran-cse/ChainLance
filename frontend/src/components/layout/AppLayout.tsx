import { useEffect, useState, type ReactNode } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  FilePenLine,
  FileSignature,
  FolderKanban,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  Send,
  ServerCog,
  UserRound,
  Wallet,
  X,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { cn } from '../../utils/cn';
import { humanizeEnum, initials } from '../../utils/format';
import type { Role } from '../../types/api';
import { SampleBanner } from '../ui/States';
import { Logo } from './Logo';

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
}

const icon = 'size-[18px] shrink-0';

/** Role-based navigation. Future milestone, submission, dispute and escrow pages slot in as new items here. */
function navFor(role: Role): NavItem[] {
  const common: NavItem[] = [
    { to: '/app/wallet', label: 'Wallet', icon: <Wallet aria-hidden className={icon} /> },
    { to: '/app/profile', label: 'Profile', icon: <UserRound aria-hidden className={icon} /> },
  ];
  const dash: NavItem = { to: '/app', label: 'Dashboard', icon: <LayoutDashboard aria-hidden className={icon} />, end: true };
  const agreements: NavItem = {
    to: '/app/agreements',
    label: 'Agreements',
    icon: <FileSignature aria-hidden className={icon} />,
  };
  if (role === 'CLIENT') {
    return [
      dash,
      { to: '/app/projects', label: 'My projects', icon: <FolderKanban aria-hidden className={icon} />, end: true },
      { to: '/app/projects/new', label: 'New project', icon: <FilePenLine aria-hidden className={icon} /> },
      agreements,
      ...common,
    ];
  }
  if (role === 'FREELANCER') {
    return [
      dash,
      { to: '/projects', label: 'Find projects', icon: <Search aria-hidden className={icon} /> },
      { to: '/app/proposals', label: 'My proposals', icon: <Send aria-hidden className={icon} /> },
      agreements,
      ...common,
    ];
  }
  return [dash, { to: '/api-status', label: 'Backend progress', icon: <ServerCog aria-hidden className={icon} /> }, ...common];
}

function itemClass({ isActive }: { isActive: boolean }) {
  return cn(
    'flex items-center gap-3 rounded-md border px-3 py-2.5 text-sm font-medium transition-colors',
    isActive ? 'border-ink bg-lime text-ink' : 'border-transparent text-ink hover:bg-stone',
  );
}

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setOpen(false);
    window.scrollTo(0, 0);
  }, [location.pathname]);

  if (!user) return null;
  const items = navFor(user.role);

  const nav = (
    <nav aria-label="Workspace" className="flex flex-col gap-1">
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} end={item.end} className={itemClass}>
          {item.icon}
          {item.label}
        </NavLink>
      ))}
    </nav>
  );

  const account = (
    <div className="border-t border-line pt-4">
      <div className="flex items-center gap-3 px-1">
        <span
          aria-hidden
          className="grid size-10 shrink-0 place-items-center rounded-md border border-ink bg-ink font-medium text-lime"
        >
          {initials(user.displayName)}
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{user.displayName}</p>
          <p className="truncate text-xs text-mute">{humanizeEnum(user.role)}</p>
        </div>
      </div>
      <button
        type="button"
        onClick={() => {
          logout();
          navigate('/', { replace: true });
        }}
        className="mt-3 flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium text-ink hover:bg-stone"
      >
        <LogOut aria-hidden className={icon} />
        Sign out
      </button>
    </div>
  );

  return (
    <div className="min-h-dvh">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-md focus:bg-lime focus:px-4 focus:py-2"
      >
        Skip to content
      </a>
      <SampleBanner />
      <div className="lg:grid lg:grid-cols-[17rem_1fr]">
        {/* Desktop sidebar */}
        <aside className="hidden h-dvh flex-col justify-between gap-8 border-r border-line bg-paper-raised p-5 lg:sticky lg:top-0 lg:flex">
          <div className="flex flex-col gap-8">
            <Logo to="/app" />
            {nav}
          </div>
          {account}
        </aside>

        {/* Mobile top bar */}
        <div className="sticky top-0 z-40 flex h-14 items-center justify-between border-b border-line bg-paper px-4 lg:hidden">
          <Logo to="/app" />
          <button
            type="button"
            className="-mr-2 rounded-md p-2"
            aria-expanded={open}
            aria-controls="workspace-menu"
            aria-label={open ? 'Close menu' : 'Open menu'}
            onClick={() => setOpen((v) => !v)}
          >
            {open ? <X aria-hidden className="size-6" /> : <Menu aria-hidden className="size-6" />}
          </button>
        </div>
        {open && (
          <div id="workspace-menu" className="flex flex-col gap-6 border-b border-line bg-paper-raised p-4 lg:hidden">
            {nav}
            {account}
          </div>
        )}

        <main id="main" className="min-w-0 px-4 py-8 sm:px-8 lg:px-12 lg:py-12">
          <div key={location.pathname} className="animate-rise mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
