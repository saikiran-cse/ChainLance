import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthShell } from '../components/layout/AuthShell';
import { Button } from '../components/ui/Button';
import { TextField } from '../components/ui/Field';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { toAppError } from '../utils/errors';
import { cn } from '../utils/cn';
import { hasErrors, validateRegister, type FieldErrors } from '../utils/validation';

type RoleChoice = 'CLIENT' | 'FREELANCER';

const ROLES: Array<{ value: RoleChoice; title: string; body: string }> = [
  { value: 'CLIENT', title: 'I want to hire', body: 'Post projects, review proposals and fund milestones.' },
  { value: 'FREELANCER', title: 'I want to work', body: 'Find projects, send proposals and get paid per milestone.' },
];

export default function RegisterPage() {
  useDocumentTitle('Create account');
  const { register } = useAuth();
  const navigate = useNavigate();

  const [role, setRole] = useState<RoleChoice | ''>('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors<'displayName' | 'email' | 'password' | 'role'>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validateRegister({ displayName, email, password, role });
    setErrors(found);
    setFormError(null);
    if (hasErrors(found) || (role !== 'CLIENT' && role !== 'FREELANCER')) return;
    setBusy(true);
    try {
      // The backend signs the user in as part of registration, so no second request is needed.
      await register({ displayName: displayName.trim(), email: email.trim(), password, role });
      navigate('/app', { replace: true });
    } catch (e) {
      const err = toAppError(e);
      const next: FieldErrors<'displayName' | 'email' | 'password' | 'role'> = {
        displayName: err.fieldErrors.displayName,
        email: err.fieldErrors.email ?? (err.code === 'EMAIL_TAKEN' ? err.message : undefined),
        password: err.fieldErrors.password ?? (err.code === 'PASSWORD_TOO_LONG' ? err.message : undefined),
        role: err.fieldErrors.role,
      };
      setErrors(next);
      if (!hasErrors(next)) setFormError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Create your account" intro="Choose how you will use ChainLance. You can still browse projects either way.">
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium">How will you use ChainLance?</legend>
          <div className="grid gap-3 sm:grid-cols-2">
            {ROLES.map((r) => (
              <label
                key={r.value}
                className={cn(
                  'flex cursor-pointer flex-col gap-1 rounded-md border p-4 transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ink',
                  role === r.value ? 'border-ink bg-lime' : 'border-line bg-paper-raised hover:border-ink',
                )}
              >
                <input
                  type="radio"
                  name="role"
                  value={r.value}
                  checked={role === r.value}
                  onChange={() => setRole(r.value)}
                  className="sr-only"
                />
                <span className="font-semibold">{r.title}</span>
                <span className="text-sm text-ink-soft">{r.body}</span>
              </label>
            ))}
          </div>
          {errors.role && (
            <p role="alert" className="mt-1.5 text-sm font-medium text-brick">
              {errors.role}
            </p>
          )}
        </fieldset>
        <TextField
          label="Display name"
          autoComplete="name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          error={errors.displayName}
        />
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
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
          hint="At least 8 characters."
        />
        {formError && (
          <p role="alert" className="rounded-md border border-brick px-4 py-3 text-sm font-medium text-brick">
            {formError}
          </p>
        )}
        <Button type="submit" size="lg" variant="accent" loading={busy}>
          Create account
        </Button>
      </form>
      <p className="mt-6 text-sm text-mute">
        Already registered?{' '}
        <Link to="/login" className="font-medium text-ink underline underline-offset-4">
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}
