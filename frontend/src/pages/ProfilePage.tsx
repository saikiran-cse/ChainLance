import { useState, type FormEvent } from 'react';
import { Button } from '../components/ui/Button';
import { TextArea, TextField } from '../components/ui/Field';
import { DefinitionRow, PageHeader, Panel } from '../components/ui/Panel';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useToast } from '../hooks/useToast';
import { toAppError } from '../utils/errors';
import { formatDate, humanizeEnum } from '../utils/format';
import { hasErrors, validateProfile, type FieldErrors } from '../utils/validation';

export default function ProfilePage() {
  useDocumentTitle('Profile');
  const { user, updateProfile } = useAuth();
  const toast = useToast();
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [bio, setBio] = useState(user?.bio ?? '');
  const [errors, setErrors] = useState<FieldErrors<'displayName' | 'bio'>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!user) return null;
  const dirty = displayName.trim() !== user.displayName || bio.trim() !== (user.bio ?? '');

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validateProfile({ displayName, bio });
    setErrors(found);
    setFormError(null);
    if (hasErrors(found)) return;
    setBusy(true);
    try {
      const saved = await updateProfile({ displayName: displayName.trim(), bio: bio.trim() || undefined });
      setDisplayName(saved.displayName);
      setBio(saved.bio ?? '');
      toast.push({ tone: 'success', message: 'Profile saved.' });
    } catch (e) {
      const err = toAppError(e);
      setErrors({ displayName: err.fieldErrors.displayName, bio: err.fieldErrors.bio });
      if (Object.keys(err.fieldErrors).length === 0) setFormError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <PageHeader title="Profile">How you appear to clients and freelancers on proposals and agreements.</PageHeader>
      <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <Panel title="Public details">
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
            <TextField
              label="Display name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              error={errors.displayName}
              maxLength={100}
            />
            <TextArea
              label="Bio"
              optional
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              error={errors.bio}
              hint={`${bio.length} of 2000 characters`}
              rows={6}
            />
            {formError && (
              <p role="alert" className="rounded-md border border-brick px-4 py-3 text-sm font-medium text-brick">
                {formError}
              </p>
            )}
            <div>
              <Button type="submit" variant="primary" loading={busy} disabled={!dirty}>
                Save changes
              </Button>
            </div>
          </form>
        </Panel>
        <Panel title="Account">
          <dl>
            <DefinitionRow term="Email">{user.email}</DefinitionRow>
            <DefinitionRow term="Role">{humanizeEnum(user.role)}</DefinitionRow>
            <DefinitionRow term="Member since">{formatDate(user.createdAt)}</DefinitionRow>
          </dl>
          <p className="mt-4 text-sm text-mute">Email and role cannot be changed here.</p>
        </Panel>
      </div>
    </div>
  );
}
