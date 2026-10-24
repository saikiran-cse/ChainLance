import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Skeleton } from '../components/ui/States';
import { Button } from '../components/ui/Button';
import { useAuth } from '../hooks/useAuth';
import type { Role } from '../types/api';

function FullPageLoading() {
  return (
    <div className="mx-auto max-w-3xl px-6 py-24" role="status" aria-label="Restoring your session">
      <Skeleton className="h-12 w-2/3" />
      <Skeleton className="mt-6 h-40 w-full" />
    </div>
  );
}

/**
 * Keeps signed-out visitors out of /app. This is a usability gate only: the backend enforces real permissions
 * on every request (SecurityConfig and per-endpoint checks) no matter what the UI shows.
 */
export function RequireAuth({ roles }: { roles?: Role[] }) {
  const { status, user, bootError, retry } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <FullPageLoading />;

  if (status === 'error') {
    return (
      <div className="mx-auto max-w-xl px-6 py-24">
        <h1 className="font-display text-4xl">We could not restore your session</h1>
        <p className="mt-3 text-mute">{bootError?.message ?? 'The API did not respond.'}</p>
        <p className="mt-2 text-mute">Your sign-in was kept. Once the backend is reachable you can continue where you left off.</p>
        <Button className="mt-6" onClick={retry}>
          Try again
        </Button>
      </div>
    );
  }

  if (status === 'anonymous' || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/app" replace state={{ denied: true }} />;
  }

  return <Outlet />;
}

/** Login and register are for signed-out visitors only. */
export function RedirectIfAuthed() {
  const { status } = useAuth();
  if (status === 'authenticated') return <Navigate to="/app" replace />;
  return <Outlet />;
}
