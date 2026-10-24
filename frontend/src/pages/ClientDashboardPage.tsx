import { Link } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { ProjectRow } from '../components/projects/ProjectCard';
import { ProposalRow } from '../components/proposals/ProposalRow';
import { Async } from '../components/ui/Async';
import { ButtonLink } from '../components/ui/Button';
import { PageHeader, Panel, Stat } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import { WalletNudge } from '../components/wallet/WalletNudge';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { projectsService } from '../services/projects.service';
import { proposalsService } from '../services/proposals.service';
import type { Project, Proposal } from '../types/marketplace';
import { greeting } from '../utils/format';

/** Proposals for every project this client has published. One request per project: there is no aggregate endpoint. */
async function loadIncomingProposals(projects: Project[]): Promise<Proposal[]> {
  const live = projects.filter((p) => p.status === 'OPEN' || p.status === 'IN_PROGRESS');
  const lists = await Promise.all(live.map((p) => proposalsService.forProject(p.id)));
  return lists.flat().map((pr) => ({ ...pr, projectTitle: pr.projectTitle ?? projects.find((p) => p.id === pr.projectId)?.title }));
}

export default function ClientDashboardPage() {
  useDocumentTitle('Dashboard');
  const { user } = useAuth();
  const projects = useResource(() => projectsService.mine(), []);
  const proposalsLoad = useResource(async () => {
    if (projects.error) throw projects.error; // the same failure the projects panel already explains
    return projects.data ? loadIncomingProposals(projects.data) : [];
  }, [projects.data, projects.error]);
  // Until the projects list has arrived there is nothing to ask about, so keep showing a skeleton instead of "empty".
  const proposals = projects.data || projects.error ? proposalsLoad : { ...proposalsLoad, data: undefined, error: null };

  const list = projects.data ?? [];
  const count = (s: Project['status']) => list.filter((p) => p.status === s).length;
  const awaiting = (proposals.data ?? []).filter((p) => p.status === 'SUBMITTED');
  const negotiating = (proposals.data ?? []).filter((p) => p.status === 'SHORTLISTED' || p.status === 'ACCEPTED');

  return (
    <div className="flex flex-col gap-10">
      <PageHeader
        title={`${greeting()}, ${user?.displayName.split(' ')[0] ?? 'there'}.`}
        actions={
          <ButtonLink to="/app/projects/new" variant="accent" icon={<Plus aria-hidden className="size-4" />}>
            Create project
          </ButtonLink>
        }
      >
        Your projects, the proposals waiting on you and the negotiations in progress.
      </PageHeader>

      <WalletNudge />

      <Async state={projects} rows={2}>
        {(all) => (
          <div className="stagger grid grid-cols-2 gap-6 md:grid-cols-4">
            <Stat label="All projects" value={all.length} />
            <Stat label="Open for proposals" value={count('OPEN')} />
            <Stat label="In progress" value={count('IN_PROGRESS')} />
            <Stat label="Drafts" value={count('DRAFT')} />
          </div>
        )}
      </Async>

      <div className="grid gap-8 lg:grid-cols-2">
        <Panel title="Active projects" action={<Link to="/app/projects" className="text-sm font-medium underline underline-offset-4">View all</Link>}>
          <Async state={projects}>
            {(all) => {
              const active = all.filter((p) => ['DRAFT', 'OPEN', 'IN_PROGRESS'].includes(p.status)).slice(0, 5);
              return active.length === 0 ? (
                <EmptyState
                  title="No active projects"
                  action={
                    <ButtonLink to="/app/projects/new" variant="primary">
                      Post your first project
                    </ButtonLink>
                  }
                >
                  Describe the work, set a budget and a deadline, and freelancers can start sending proposals.
                </EmptyState>
              ) : (
                <ul>
                  {active.map((p) => (
                    <ProjectRow
                      key={p.id}
                      project={p}
                      to={p.status === 'DRAFT' ? `/app/projects/${p.id}/edit` : `/app/projects/${p.id}/proposals`}
                    />
                  ))}
                </ul>
              );
            }}
          </Async>
        </Panel>

        <Panel title="Incoming proposals">
          <Async state={proposals}>
            {() =>
              awaiting.length === 0 ? (
                <EmptyState title="Nothing to review">New proposals on your open projects show up here.</EmptyState>
              ) : (
                <ul>
                  {awaiting.slice(0, 5).map((p) => (
                    <ProposalRow key={p.id} proposal={p} showFreelancer to={`/app/projects/${p.projectId}/proposals`} />
                  ))}
                </ul>
              )
            }
          </Async>
        </Panel>
      </div>

      <Panel title="Recent negotiation activity">
        <Async state={proposals}>
          {() =>
            negotiating.length === 0 ? (
              <EmptyState title="No negotiations yet">
                Shortlist a proposal to open a thread where you and the freelancer trade offers.
              </EmptyState>
            ) : (
              <ul>
                {negotiating.slice(0, 6).map((p) => (
                  <ProposalRow key={p.id} proposal={p} showFreelancer to={`/app/proposals/${p.id}/negotiation`} />
                ))}
              </ul>
            )
          }
        </Async>
      </Panel>
    </div>
  );
}
