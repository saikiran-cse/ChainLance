import { useState } from 'react';
import { Async } from '../components/ui/Async';
import { Button, ButtonLink } from '../components/ui/Button';
import { PageHeader, Panel } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import { ProposalRow } from '../components/proposals/ProposalRow';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { useToast } from '../hooks/useToast';
import { proposalsService } from '../services/proposals.service';
import { messageOf } from '../utils/errors';

export default function MyProposalsPage() {
  useDocumentTitle('My proposals');
  const toast = useToast();
  const proposals = useResource(() => proposalsService.mine(), []);
  const [withdrawing, setWithdrawing] = useState<number | null>(null);

  async function withdraw(id: number) {
    if (!window.confirm('Are you sure you want to withdraw this proposal?')) return;
    setWithdrawing(id);
    try {
      await proposalsService.withdraw(id);
      toast.push({ tone: 'success', message: 'Proposal withdrawn.' });
      proposals.reload();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setWithdrawing(null);
    }
  }

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
                {items.map((p) => {
                  const canWithdraw = p.status === 'SUBMITTED' || p.status === 'SHORTLISTED';
                  return (
                    <ProposalRow
                      key={p.id}
                      proposal={p}
                      to={`/app/proposals/${p.id}/negotiation`}
                      trailing={
                        <div className="flex items-center gap-2">
                          <ButtonLink size="sm" variant="outline" to={`/app/proposals/${p.id}/negotiation`}>
                            Open thread
                          </ButtonLink>
                          {canWithdraw && (
                            <Button
                              size="sm"
                              variant="ghost"
                              className="text-brick hover:bg-brick/10"
                              loading={withdrawing === p.id}
                              onClick={() => void withdraw(p.id)}
                            >
                              Withdraw
                            </Button>
                          )}
                        </div>
                      }
                    />
                  );
                })}
              </ul>
            </Panel>
          )
        }
      </Async>
    </div>
  );
}
