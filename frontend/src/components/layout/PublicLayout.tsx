import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { SampleBanner } from '../ui/States';
import { SiteFooter } from './SiteFooter';
import { SiteHeader } from './SiteHeader';

/** Scrolls to #hash targets (React Router does not do this on its own) and to the top on page changes. */
function useScrollManagement() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    if (hash) {
      const el = document.getElementById(hash.slice(1));
      if (el) {
        el.scrollIntoView();
        return;
      }
    }
    window.scrollTo(0, 0);
  }, [pathname, hash]);
}

export function PublicLayout() {
  useScrollManagement();
  const { pathname } = useLocation();
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-md focus:bg-lime focus:px-4 focus:py-2"
      >
        Skip to content
      </a>
      <SampleBanner />
      <SiteHeader />
      <main id="main" key={pathname} className={pathname === '/' ? 'flex-1' : 'animate-rise flex-1'}>
        <Outlet />
      </main>
      <SiteFooter />
    </div>
  );
}
