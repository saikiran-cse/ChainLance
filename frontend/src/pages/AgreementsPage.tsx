import { Link } from 'react-router-dom';
import { Async } from '../components/ui/Async';
import { PageHeader, Panel } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import { StatusBadge } from '../components/ui/StatusBadge';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { agreementsService } from '../services/agreements.service';
import { formatDate, formatInr } from '../utils/format';

export default function AgreementsPage() {
  useDocumentTitle('Agreements');
  const agreements = useResource(() => agreementsService.mine(), []);
  return (
    <div className="flex flex-col gap-8">
      <PageHeader title="Agreements">Agreed terms, who has signed off, and where each one stands.</PageHeader>
      <Async state={agreements} rows={3}>
        {(items) =>
          items.length === 0 ? (
            <EmptyState title="No agreements yet">
              An agreement is created when a proposal is accepted. Negotiate the terms first, then finalize them here.
            </EmptyState>
          ) : (
            <Panel padded={false}>
              <ul>
                {items.map((a) => (
                  <li key={a.id} className="grid gap-3 border-b border-line p-5 last:border-b-0 sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-8">
                    <div className="min-w-0">
                      <Link to={`/app/agreements/${a.id}`} className="font-display text-2xl leading-tight underline-offset-4 hover:underline">
                        {a.projectTitle ?? `Agreement ${a.id}`}
                      </Link>
                      <p className="text-sm text-mute">
                        {a.clientName ?? 'Client'} and {a.freelancerName ?? 'freelancer'}
                        {a.createdAt ? `, created ${formatDate(a.createdAt)}` : ''}
                      </p>
                    </div>
                    <p className="font-semibold">{a.priceInr != null ? formatInr(a.priceInr) : '—'}</p>
                    <StatusBadge status={a.status} />
                  </li>
                ))}
              </ul>
            </Panel>
          )
        }
      </Async>
    </div>
  );
}
