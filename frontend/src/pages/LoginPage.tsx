import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AuthShell } from '../components/layout/AuthShell';
import { Button } from '../components/ui/Button';
import { TextField } from '../components/ui/Field';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { toAppError } from '../utils/errors';
import { hasErrors, validateLogin, type FieldErrors } from '../utils/validation';

export default function LoginPage() {
  useDocumentTitle('Sign in');
  const { login, sessionExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors<'email' | 'password'>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validateLogin({ email, password });
    setErrors(found);
    setFormError(null);
    if (hasErrors(found)) return;
    setBusy(true);
    try {
      await login({ email: email.trim(), password });
      navigate(from && from.startsWith('/') ? from : '/app', { replace: true });
    } catch (e) {
      const err = toAppError(e);
      setErrors({ email: err.fieldErrors.email, password: err.fieldErrors.password });
      // The backend answers wrong email and wrong password identically on purpose.
      setFormError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Sign in" intro="Pick up your projects, proposals and agreements where you left them.">
      {sessionExpired && (
        <p role="status" className="mb-6 rounded-md border border-ochre px-4 py-3 text-sm text-ochre">
          Your session ended. Sign in again to continue.
        </p>
      )}
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        {formError && (
          <p role="alert" className="rounded-md border border-brick px-4 py-3 text-sm font-medium text-brick">
            {formError}
          </p>
        )}
        <Button type="submit" size="lg" variant="accent" loading={busy}>
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-sm text-mute">
        No account yet?{' '}
        <Link to="/register" className="font-medium text-ink underline underline-offset-4">
          Create one
        </Link>
      </p>
    </AuthShell>
  );
}
