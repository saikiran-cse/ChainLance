import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { Async } from '../components/ui/Async';
import { Button, ButtonLink } from '../components/ui/Button';
import { PageHeader, Panel } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import { StatusBadge } from '../components/ui/StatusBadge';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { useToast } from '../hooks/useToast';
import { projectsService } from '../services/projects.service';
import type { Project } from '../types/marketplace';
import { messageOf } from '../utils/errors';
import { formatDate, formatInr } from '../utils/format';

export default function MyProjectsPage() {
  useDocumentTitle('My projects');
  const toast = useToast();
  const projects = useResource(() => projectsService.mine(), []);
  const [working, setWorking] = useState<number | null>(null);

  async function act(project: Project, action: 'publish' | 'close') {
    setWorking(project.id);
    try {
      await (action === 'publish' ? projectsService.publish(project.id) : projectsService.close(project.id));
      toast.push({ tone: 'success', message: action === 'publish' ? 'Project published.' : 'Project closed.' });
      projects.reload();
    } catch (e) {
      toast.push({ tone: 'error', message: messageOf(e) });
    } finally {
      setWorking(null);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        title="My projects"
        actions={
          <ButtonLink to="/app/projects/new" variant="accent" icon={<Plus aria-hidden className="size-4" />}>
            New project
          </ButtonLink>
        }
      >
        Drafts, open listings and work in progress.
      </PageHeader>
      <Async state={projects} rows={4}>
        {(items) =>
          items.length === 0 ? (
            <EmptyState
              title="You have not posted a project"
              action={
                <ButtonLink to="/app/projects/new" variant="primary">
                  Post a project
                </ButtonLink>
              }
            >
              A draft stays private until you publish it.
            </EmptyState>
          ) : (
            <Panel padded={false}>
              <ul>
                {items.map((p) => (
                  <li key={p.id} className="grid gap-4 border-b border-line p-5 last:border-b-0 lg:grid-cols-[1fr_auto_auto] lg:items-center lg:gap-8">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-3">
                        <Link
                          to={p.status === 'DRAFT' ? `/app/projects/${p.id}/edit` : `/projects/${p.id}`}
                          className="font-display text-2xl leading-tight underline-offset-4 hover:underline"
                        >
                          {p.title}
                        </Link>
                        <StatusBadge status={p.status} />
                      </div>
                      <p className="mt-1 text-sm text-mute">
                        {p.category}, created {formatDate(p.createdAt)}
                      </p>
                    </div>
                    <p className="font-semibold">{formatInr(p.budgetInr)}</p>
                    <div className="flex flex-wrap gap-2">
                      {p.status === 'DRAFT' && (
                        <>
                          <ButtonLink size="sm" variant="outline" to={`/app/projects/${p.id}/edit`}>
                            Edit
                          </ButtonLink>
                          <Button size="sm" variant="accent" loading={working === p.id} onClick={() => void act(p, 'publish')}>
                            Publish
                          </Button>
                        </>
                      )}
                      {(p.status === 'OPEN' || p.status === 'IN_PROGRESS') && (
                        <ButtonLink size="sm" variant="primary" to={`/app/projects/${p.id}/proposals`}>
                          Proposals
                        </ButtonLink>
                      )}
                      {p.status === 'OPEN' && (
                        <>
                          <ButtonLink size="sm" variant="outline" to={`/app/projects/${p.id}/edit`}>
                            Edit
                          </ButtonLink>
                          <Button size="sm" variant="danger" loading={working === p.id} onClick={() => void act(p, 'close')}>
                            Close
                          </Button>
                        </>
                      )}
                    </div>
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
