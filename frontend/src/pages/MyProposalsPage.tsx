import { Async } from '../components/ui/Async';
import { ButtonLink } from '../components/ui/Button';
import { PageHeader, Panel } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import { ProposalRow } from '../components/proposals/ProposalRow';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { proposalsService } from '../services/proposals.service';

export default function MyProposalsPage() {
  useDocumentTitle('My proposals');
  const proposals = useResource(() => proposalsService.mine(), []);
  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        title="My proposals"
        actions={
          <ButtonLink to="/projects" variant="accent">
            Find projects
          </ButtonLink>
        }
      >
        Every proposal you have sent and where it stands.
      </PageHeader>
      <Async state={proposals} rows={4}>
        {(items) =>
          items.length === 0 ? (
            <EmptyState
              title="No proposals yet"
              action={
                <ButtonLink to="/projects" variant="primary">
                  Browse projects
                </ButtonLink>
              }
            >
              Proposals you send show up here with their status.
            </EmptyState>
          ) : (
            <Panel>
              <ul>
                {items.map((p) => (
                  <ProposalRow
                    key={p.id}
                    proposal={p}
                    to={p.status === 'SHORTLISTED' || p.status === 'ACCEPTED' ? `/app/proposals/${p.id}/negotiation` : `/projects/${p.projectId}`}
                    trailing={
                      p.status === 'SHORTLISTED' || p.status === 'ACCEPTED' ? (
                        <ButtonLink size="sm" variant="outline" to={`/app/proposals/${p.id}/negotiation`}>
                          Open thread
                        </ButtonLink>
                      ) : undefined
                    }
                  />
                ))}
              </ul>
            </Panel>
          )
        }
      </Async>
    </div>
  );
}
