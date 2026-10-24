import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { DeadlineLabel } from '../components/projects/ProjectCard';
import { Async } from '../components/ui/Async';
import { Button } from '../components/ui/Button';
import { TextArea, TextField } from '../components/ui/Field';
import { PageHeader, Panel } from '../components/ui/Panel';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { useToast } from '../hooks/useToast';
import { projectsService } from '../services/projects.service';
import { proposalsService } from '../services/proposals.service';
import { toAppError } from '../utils/errors';
import { formatInr } from '../utils/format';
import { hasErrors, splitList, toMoney, validateProposal, type FieldErrors, type ProposalFormValues } from '../utils/validation';

export default function SubmitProposalPage() {
  useDocumentTitle('Send a proposal');
  const { id } = useParams();
  const projectId = Number(id);
  const navigate = useNavigate();
  const toast = useToast();
  const project = useResource(() => projectsService.get(projectId), [projectId]);

  const [values, setValues] = useState<ProposalFormValues>({ message: '', price: '', durationDays: '', links: '' });
  const [errors, setErrors] = useState<FieldErrors<keyof ProposalFormValues>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (key: keyof ProposalFormValues, value: string) => setValues((v) => ({ ...v, [key]: value }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validateProposal(values);
    setErrors(found);
    setFormError(null);
    if (hasErrors(found)) return;
    setBusy(true);
    try {
      await proposalsService.submit(projectId, {
        message: values.message,
        priceInr: toMoney(values.price),
        durationDays: Number(values.durationDays),
        portfolioLinks: splitList(values.links),
      });
      toast.push({ tone: 'success', message: 'Proposal sent.' });
      navigate('/app/proposals');
    } catch (e) {
      const err = toAppError(e);
      const mapped: FieldErrors<keyof ProposalFormValues> = {
        message: err.fieldErrors.message,
        price: err.fieldErrors.priceInr,
        durationDays: err.fieldErrors.durationDays,
        links: err.fieldErrors.portfolioLinks,
      };
      setErrors(mapped);
      if (!hasErrors(mapped)) {
        setFormError(
          err.kind === 'conflict' ? 'You have already sent a proposal for this project. Open "My proposals" to follow it.' : err.message,
        );
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <Link to={`/projects/${projectId}`} className="inline-flex items-center gap-2 text-sm font-medium underline-offset-4 hover:underline">
        <ArrowLeft aria-hidden className="size-4" />
        Back to project
      </Link>
      <PageHeader title="Send a proposal">Tell the client why you fit, what you will charge and how long it will take.</PageHeader>

      <div className="grid gap-8 lg:grid-cols-[1.5fr_1fr]">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
          <TextArea
            label="Cover letter"
            rows={9}
            value={values.message}
            onChange={(e) => set('message', e.target.value)}
            error={errors.message}
            hint="Mention similar work you have done and how you would approach this project."
          />
          <div className="grid gap-5 sm:grid-cols-2">
            <TextField
              label="Proposed price in INR"
              inputMode="decimal"
              value={values.price}
              onChange={(e) => set('price', e.target.value)}
              error={errors.price}
            />
            <TextField
              label="Estimated duration in days"
              inputMode="numeric"
              value={values.durationDays}
              onChange={(e) => set('durationDays', e.target.value)}
              error={errors.durationDays}
            />
          </div>
          <TextArea
            label="Portfolio links"
            optional
            rows={3}
            value={values.links}
            onChange={(e) => set('links', e.target.value)}
            error={errors.links}
            hint="One link per line, starting with https://"
          />
          {formError && (
            <p role="alert" className="rounded-md border border-brick px-4 py-3 text-sm font-medium text-brick">
              {formError}
            </p>
          )}
          <div>
            <Button type="submit" variant="accent" size="lg" loading={busy}>
              Send proposal
            </Button>
          </div>
        </form>

        <aside>
          <Panel title="The project">
            <Async state={project} rows={3}>
              {(p) => (
                <div className="flex flex-col gap-3">
                  <p className="font-display text-2xl leading-tight">{p.title}</p>
                  <dl className="grid gap-3 text-sm">
                    <div>
                      <dt className="text-mute">Client budget</dt>
                      <dd className="font-semibold">{formatInr(p.budgetInr)}</dd>
                    </div>
                    <div>
                      <dt className="text-mute">Deadline</dt>
                      <dd>
                        <DeadlineLabel iso={p.deadline} />
                      </dd>
                    </div>
                  </dl>
                  <p className="line-clamp-6 text-sm text-ink-soft">{p.description}</p>
                </div>
              )}
            </Async>
          </Panel>
        </aside>
      </div>
    </div>
  );
}
