import { Link } from 'react-router-dom';
import { Search } from 'lucide-react';
import { ProjectRow } from '../components/projects/ProjectCard';
import { ProposalRow } from '../components/proposals/ProposalRow';
import { Async } from '../components/ui/Async';
import { ButtonLink } from '../components/ui/Button';
import { PageHeader, Panel, Stat } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import { StatusBadge } from '../components/ui/StatusBadge';
import { WalletNudge } from '../components/wallet/WalletNudge';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { agreementsService } from '../services/agreements.service';
import { projectsService } from '../services/projects.service';
import { proposalsService } from '../services/proposals.service';
import { formatInr, greeting } from '../utils/format';

export default function FreelancerDashboardPage() {
  useDocumentTitle('Dashboard');
  const { user } = useAuth();
  const open = useResource(() => projectsService.list({}), []);
  const mine = useResource(() => proposalsService.mine(), []);
  const agreements = useResource(() => agreementsService.mine(), []);

  const proposals = mine.data ?? [];
  const count = (s: string) => proposals.filter((p) => p.status === s).length;
  const inNegotiation = proposals.filter((p) => p.status === 'SHORTLISTED' || p.status === 'ACCEPTED');
  const appliedTo = new Set(proposals.map((p) => p.projectId));

  return (
    <div className="flex flex-col gap-10">
      <PageHeader
        title={`${greeting()}, ${user?.displayName.split(' ')[0] ?? 'there'}.`}
        actions={
          <ButtonLink to="/projects" variant="accent" icon={<Search aria-hidden className="size-4" />}>
            Find projects
          </ButtonLink>
        }
      >
        Open projects, the status of your proposals and the agreements you are working under.
      </PageHeader>

      <WalletNudge />

      <Async state={mine} rows={2}>
        {(all) => (
          <div className="stagger grid grid-cols-2 gap-6 md:grid-cols-4">
            <Stat label="Proposals sent" value={all.length} />
            <Stat label="Awaiting a reply" value={count('SUBMITTED')} />
            <Stat label="Shortlisted" value={count('SHORTLISTED')} />
            <Stat label="Accepted" value={count('ACCEPTED')} />
          </div>
        )}
      </Async>

      <div className="grid gap-8 lg:grid-cols-2">
        <Panel title="Open projects" action={<Link to="/projects" className="text-sm font-medium underline underline-offset-4">Browse all</Link>}>
          <Async state={open}>
            {(all) => {
              const fresh = all.filter((p) => !appliedTo.has(p.id)).slice(0, 5);
              return fresh.length === 0 ? (
                <EmptyState title="No open projects right now">Check back soon, or widen your search on the projects page.</EmptyState>
              ) : (
                <ul>
                  {fresh.map((p) => (
                    <ProjectRow key={p.id} project={p} to={`/projects/${p.id}`} />
                  ))}
                </ul>
              );
            }}
          </Async>
        </Panel>

        <Panel title="Proposal status" action={<Link to="/app/proposals" className="text-sm font-medium underline underline-offset-4">View all</Link>}>
          <Async state={mine}>
            {(all) =>
              all.length === 0 ? (
                <EmptyState
                  title="You have not sent a proposal yet"
                  action={
                    <ButtonLink to="/projects" variant="primary">
                      Find a project
                    </ButtonLink>
                  }
                >
                  Open a project that fits your skills and send a cover letter with your price and timeline.
                </EmptyState>
              ) : (
                <ul>
                  {all.slice(0, 5).map((p) => (
                    <ProposalRow
                      key={p.id}
                      proposal={p}
                      to={p.status === 'SHORTLISTED' || p.status === 'ACCEPTED' ? `/app/proposals/${p.id}/negotiation` : `/projects/${p.projectId}`}
                    />
                  ))}
                </ul>
              )
            }
          </Async>
        </Panel>
      </div>

      <div className="grid gap-8 lg:grid-cols-2">
        <Panel title="Negotiations">
          <Async state={mine}>
            {() =>
              inNegotiation.length === 0 ? (
                <EmptyState title="No open negotiations">
                  When a client shortlists your proposal, the thread appears here.
                </EmptyState>
              ) : (
                <ul>
                  {inNegotiation.map((p) => (
                    <ProposalRow key={p.id} proposal={p} to={`/app/proposals/${p.id}/negotiation`} />
                  ))}
                </ul>
              )
            }
          </Async>
        </Panel>

        <Panel title="Active agreements" action={<Link to="/app/agreements" className="text-sm font-medium underline underline-offset-4">View all</Link>}>
          <Async state={agreements}>
            {(all) => {
              const active = all.filter((a) => a.status !== 'CANCELLED' && a.status !== 'COMPLETED');
              return active.length === 0 ? (
                <EmptyState title="No active agreements">An agreement is created once a client accepts your proposal.</EmptyState>
              ) : (
                <ul>
                  {active.slice(0, 5).map((a) => (
                    <li key={a.id} className="flex items-center justify-between gap-4 border-b border-line py-4 last:border-b-0">
                      <div className="min-w-0">
                        <Link to={`/app/agreements/${a.id}`} className="block truncate font-medium underline-offset-4 hover:underline">
                          {a.projectTitle ?? `Agreement ${a.id}`}
                        </Link>
                        <p className="text-sm text-mute">{a.priceInr != null ? formatInr(a.priceInr) : 'Price in snapshot'}</p>
                      </div>
                      <StatusBadge status={a.status} />
                    </li>
                  ))}
                </ul>
              );
            }}
          </Async>
        </Panel>
      </div>
    </div>
  );
}
