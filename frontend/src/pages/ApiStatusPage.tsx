import { useMemo, useState } from 'react';
import { CircleCheck, Construction } from 'lucide-react';
import { env } from '../config/env';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { ENDPOINTS, endpointCounts, type EndpointStatus } from '../services/endpoints';
import { cn } from '../utils/cn';

type Filter = 'all' | EndpointStatus;

export default function ApiStatusPage() {
  useDocumentTitle('Backend progress');
  const [filter, setFilter] = useState<Filter>('all');
  const counts = endpointCounts();
  const rows = useMemo(() => ENDPOINTS.filter((e) => filter === 'all' || e.status === filter), [filter]);

  return (
    <div className="mx-auto max-w-5xl px-4 py-12 sm:px-6 lg:px-8">
      <h1 className="font-display text-6xl leading-[0.98] sm:text-7xl">Backend progress</h1>
      <p className="mt-4 max-w-2xl text-lg text-mute">
        The frontend is built against the backend as it exists today. “Live” endpoints have a controller in the repository.
        “Pending” endpoints are planned in ARCHITECTURE.md but not built, so the pages that need them say so.
      </p>

      <dl className="mt-10 grid grid-cols-3 gap-6 border-y border-line py-6">
        <div>
          <dt className="text-sm text-mute">Live</dt>
          <dd className="font-display text-5xl leading-none">{counts.live}</dd>
        </div>
        <div>
          <dt className="text-sm text-mute">Pending</dt>
          <dd className="font-display text-5xl leading-none">{counts.pending}</dd>
        </div>
        <div>
          <dt className="text-sm text-mute">Data source</dt>
          <dd className="mt-2 font-medium">{env.useMocks ? 'Sample data for pending areas' : 'Real API only'}</dd>
        </div>
      </dl>

      <div role="group" aria-label="Filter endpoints" className="mt-8 flex flex-wrap gap-2">
        {(['all', 'live', 'pending'] as const).map((f) => (
          <button
            key={f}
            type="button"
            aria-pressed={filter === f}
            onClick={() => setFilter(f)}
            className={cn(
              'rounded-md border px-3.5 py-1.5 text-sm font-medium transition-colors',
              filter === f ? 'border-ink bg-ink text-paper' : 'border-line hover:border-ink',
            )}
          >
            {f === 'all' ? 'All endpoints' : f === 'live' ? 'Live' : 'Pending'}
          </button>
        ))}
      </div>

      <div className="mt-6 overflow-x-auto rounded-lg border border-line bg-paper-raised">
        <table className="w-full min-w-[40rem] text-left text-sm">
          <caption className="sr-only">Backend endpoints and whether they exist</caption>
          <thead>
            <tr className="border-b border-line bg-stone/60">
              <th scope="col" className="px-4 py-3 font-semibold">Status</th>
              <th scope="col" className="px-4 py-3 font-semibold">Endpoint</th>
              <th scope="col" className="px-4 py-3 font-semibold">Used by</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((e) => (
              <tr key={`${e.method} ${e.path}`} className="border-b border-line align-top last:border-b-0">
                <td className="px-4 py-3">
                  <span className="inline-flex items-center gap-1.5 font-medium">
                    {e.status === 'live' ? (
                      <CircleCheck aria-hidden className="size-4" />
                    ) : (
                      <Construction aria-hidden className="size-4 text-ochre" />
                    )}
                    <span className={e.status === 'pending' ? 'text-ochre' : undefined}>{e.status === 'live' ? 'Live' : 'Pending'}</span>
                  </span>
                </td>
                <td className="px-4 py-3">
                  <code className="font-mono text-[13px]">
                    {e.method} {e.path}
                  </code>
                  {e.note && <p className="mt-1 max-w-md text-mute">{e.note}</p>}
                </td>
                <td className="px-4 py-3 text-mute">{e.usedBy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
