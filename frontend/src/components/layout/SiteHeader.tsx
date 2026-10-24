import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { cn } from '../../utils/cn';
import { ButtonLink } from '../ui/Button';
import { Logo } from './Logo';

const LINKS = [
  { to: '/projects', label: 'Projects' },
  { to: '/#how', label: 'How it works' },
  { to: '/#escrow', label: 'Escrow' },
];

function linkClass({ isActive }: { isActive: boolean }) {
  return cn(
    'rounded-sm px-2 py-1 text-sm font-medium text-ink underline-offset-8 hover:underline',
    isActive && 'underline decoration-2 decoration-lime-deep',
  );
}

export function SiteHeader() {
  const { status, user } = useAuth();
  const [open, setOpen] = useState(false);
  const signedIn = status === 'authenticated' && user;

  const actions = signedIn ? (
    <ButtonLink to="/app" variant="accent">
      Open dashboard
    </ButtonLink>
  ) : (
    <>
      <ButtonLink to="/login" variant="ghost">
        Sign in
      </ButtonLink>
      <ButtonLink to="/register" variant="accent">
        Get started
      </ButtonLink>
    </>
  );

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-paper/95 backdrop-blur-sm">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-6 px-4 sm:px-6 lg:px-8">
        <Logo />
        <nav aria-label="Main" className="hidden items-center gap-5 md:flex">
          {LINKS.map((l) => (
            <NavLink key={l.to} to={l.to} className={linkClass} end={l.to === '/'}>
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden items-center gap-2 md:flex">{actions}</div>
        <button
          type="button"
          className="-mr-2 rounded-md p-2 md:hidden"
          aria-expanded={open}
          aria-controls="mobile-nav"
          aria-label={open ? 'Close menu' : 'Open menu'}
          onClick={() => setOpen((v) => !v)}
        >
          {open ? <X aria-hidden className="size-6" /> : <Menu aria-hidden className="size-6" />}
        </button>
      </div>
      {open && (
        <div id="mobile-nav" className="border-t border-line bg-paper px-4 pb-5 pt-3 md:hidden">
          <nav aria-label="Main" className="flex flex-col">
            {LINKS.map((l) => (
              <NavLink
                key={l.to}
                to={l.to}
                onClick={() => setOpen(false)}
                className="border-b border-line py-3 text-lg font-medium"
              >
                {l.label}
              </NavLink>
            ))}
          </nav>
          <div className="mt-4 flex flex-col gap-2" onClick={() => setOpen(false)}>
            {actions}
          </div>
        </div>
      )}
    </header>
  );
}
