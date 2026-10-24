import { useState, type FormEvent } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import type { Project, ProjectInput } from '../../types/marketplace';
import { CATEGORIES } from '../../utils/categories';
import { toAppError } from '../../utils/errors';
import { formatInr } from '../../utils/format';
import {
  hasErrors,
  MAX_MILESTONES,
  splitList,
  toMoney,
  validateProject,
  type FieldErrors,
  type ProjectFormValues,
} from '../../utils/validation';
import { Button } from '../ui/Button';
import { SelectField, TextArea, TextField } from '../ui/Field';
import { Panel } from '../ui/Panel';

function fromProject(p?: Project): ProjectFormValues {
  return {
    title: p?.title ?? '',
    description: p?.description ?? '',
    category: p?.category ?? '',
    skills: p?.skills.join(', ') ?? '',
    budget: p ? String(p.budgetInr) : '',
    deadline: p?.deadline ? p.deadline.slice(0, 10) : '',
    milestones: (p?.milestones ?? []).map((m) => ({ description: m.description, allocation: String(m.allocationInr) })),
  };
}

function toInput(v: ProjectFormValues): ProjectInput {
  return {
    title: v.title.trim(),
    description: v.description.trim(),
    category: v.category.trim(),
    skills: splitList(v.skills),
    budgetInr: toMoney(v.budget),
    deadline: v.deadline ? new Date(`${v.deadline}T23:59:59`).toISOString() : null,
    milestones: v.milestones.map((m, i) => ({
      position: i + 1,
      description: m.description.trim(),
      allocationInr: toMoney(m.allocation),
    })),
  };
}

interface Props {
  initial?: Project;
  /** `publish` is true when the client chose "Publish project" instead of "Save draft". */
  onSubmit(input: ProjectInput, publish: boolean): Promise<void>;
}

export function ProjectForm({ initial, onSubmit }: Props) {
  const [values, setValues] = useState<ProjectFormValues>(() => fromProject(initial));
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'draft' | 'publish' | null>(null);

  const set = <K extends keyof ProjectFormValues>(key: K, value: ProjectFormValues[K]) =>
    setValues((v) => ({ ...v, [key]: value }));

  const categories = values.category && !CATEGORIES.some((c) => c === values.category) ? [values.category, ...CATEGORIES] : CATEGORIES;
  const budget = toMoney(values.budget);
  const allocated = values.milestones.reduce((sum, m) => {
    const n = toMoney(m.allocation);
    return sum + (Number.isFinite(n) && n > 0 ? n : 0);
  }, 0);
  const published = initial && initial.status !== 'DRAFT';

  async function submit(publish: boolean) {
    const found = validateProject(values);
    setErrors(found);
    setFormError(null);
    if (hasErrors(found)) {
      document.getElementById('project-form')?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
      return;
    }
    setBusy(publish ? 'publish' : 'draft');
    try {
      await onSubmit(toInput(values), publish);
    } catch (e) {
      const err = toAppError(e);
      // The backend is the final authority: map its field errors back onto the form.
      const mapped: FieldErrors = {};
      if (err.fieldErrors.title) mapped.title = err.fieldErrors.title;
      if (err.fieldErrors.description) mapped.description = err.fieldErrors.description;
      if (err.fieldErrors.category) mapped.category = err.fieldErrors.category;
      if (err.fieldErrors.skills) mapped.skills = err.fieldErrors.skills;
      if (err.fieldErrors.budgetInr) mapped.budget = err.fieldErrors.budgetInr;
      if (err.fieldErrors.deadline) mapped.deadline = err.fieldErrors.deadline;
      setErrors(mapped);
      if (!hasErrors(mapped)) setFormError(err.message);
    } finally {
      setBusy(null);
    }
  }

  function onFormSubmit(event: FormEvent) {
    event.preventDefault();
    // Pressing Enter saves without publishing; publishing is always an explicit button press.
    void submit(false);
  }

  return (
    <form id="project-form" onSubmit={onFormSubmit} noValidate className="flex flex-col gap-8">
      <Panel title="The work">
        <div className="flex flex-col gap-5">
          <TextField
            label="Title"
            value={values.title}
            onChange={(e) => set('title', e.target.value)}
            error={errors.title}
            maxLength={200}
            placeholder="Mobile-first ordering site for a neighbourhood bakery"
          />
          <TextArea
            label="Description"
            rows={8}
            value={values.description}
            onChange={(e) => set('description', e.target.value)}
            error={errors.description}
            hint="What needs to be built, what already exists, and what a finished result looks like."
          />
          <div className="grid gap-5 sm:grid-cols-2">
            <SelectField label="Category" value={values.category} onChange={(e) => set('category', e.target.value)} error={errors.category}>
              <option value="">Choose a category</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </SelectField>
            <TextField
              label="Required skills"
              value={values.skills}
              onChange={(e) => set('skills', e.target.value)}
              error={errors.skills}
              hint="Separate with commas."
              placeholder="React, Node.js, Payments"
            />
          </div>
          {splitList(values.skills).length > 0 && (
            <ul className="flex flex-wrap gap-1.5" aria-label="Skills preview">
              {splitList(values.skills).map((s) => (
                <li key={s} className="rounded-sm border border-line bg-paper px-2 py-0.5 text-xs">
                  {s}
                </li>
              ))}
            </ul>
          )}
        </div>
      </Panel>

      <Panel title="Budget and timing">
        <div className="grid gap-5 sm:grid-cols-2">
          <TextField
            label="Budget in INR"
            inputMode="decimal"
            value={values.budget}
            onChange={(e) => set('budget', e.target.value)}
            error={errors.budget}
            hint="Indicative. Escrow itself settles in ETH."
            placeholder="150000"
          />
          <TextField
            label="Deadline"
            type="date"
            optional
            value={values.deadline}
            onChange={(e) => set('deadline', e.target.value)}
            error={errors.deadline}
          />
        </div>
      </Panel>

      <Panel
        title="Milestone plan"
        action={
          <Button
            size="sm"
            variant="outline"
            icon={<Plus aria-hidden className="size-4" />}
            disabled={values.milestones.length >= MAX_MILESTONES}
            onClick={() => set('milestones', [...values.milestones, { description: '', allocation: '' }])}
          >
            Add milestone
          </Button>
        }
      >
        <p className="mb-5 text-sm text-mute">
          Optional. Splitting the budget into milestones tells freelancers how payment will be released. Freelancers can
          propose changes during negotiation.
        </p>
        {values.milestones.length === 0 ? (
          <p className="rounded-md border border-dashed border-warm-grey p-5 text-center text-mute">
            No milestones yet. You can leave this empty and agree on them during negotiation.
          </p>
        ) : (
          <ol className="flex flex-col gap-4">
            {values.milestones.map((m, i) => (
              <li key={i} className="grid gap-3 rounded-md border border-line bg-paper p-4 sm:grid-cols-[1fr_11rem_auto] sm:items-start">
                <TextField
                  label={`Milestone ${i + 1}`}
                  value={m.description}
                  maxLength={500}
                  error={errors[`milestone-${i}-description`]}
                  onChange={(e) =>
                    set('milestones', values.milestones.map((x, j) => (j === i ? { ...x, description: e.target.value } : x)))
                  }
                />
                <TextField
                  label="Amount in INR"
                  inputMode="decimal"
                  value={m.allocation}
                  error={errors[`milestone-${i}-allocation`]}
                  onChange={(e) =>
                    set('milestones', values.milestones.map((x, j) => (j === i ? { ...x, allocation: e.target.value } : x)))
                  }
                />
                <Button
                  variant="ghost"
                  size="sm"
                  className="sm:mt-7"
                  aria-label={`Remove milestone ${i + 1}`}
                  onClick={() => set('milestones', values.milestones.filter((_, j) => j !== i))}
                >
                  <Trash2 aria-hidden className="size-4" />
                </Button>
              </li>
            ))}
          </ol>
        )}
        {errors.milestones && (
          <p role="alert" className="mt-3 text-sm font-medium text-brick">
            {errors.milestones}
          </p>
        )}
        {values.milestones.length > 0 && (
          <p className="mt-4 text-sm text-mute" aria-live="polite">
            Allocated {formatInr(allocated)}
            {budget > 0 ? ` of ${formatInr(budget)}` : ''}
          </p>
        )}
      </Panel>

      {formError && (
        <p role="alert" className="rounded-md border border-brick px-4 py-3 text-sm font-medium text-brick">
          {formError}
        </p>
      )}

      <div className="flex flex-wrap gap-3">
        {published ? (
          <Button type="submit" variant="primary" size="lg" loading={busy === 'draft'}>
            Save changes
          </Button>
        ) : (
          <>
            <Button variant="accent" size="lg" loading={busy === 'publish'} disabled={busy === 'draft'} onClick={() => void submit(true)}>
              Publish project
            </Button>
            <Button variant="outline" size="lg" loading={busy === 'draft'} disabled={busy === 'publish'} onClick={() => void submit(false)}>
              Save draft
            </Button>
          </>
        )}
      </div>
    </form>
  );
}
