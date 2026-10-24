import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { DeadlineLabel, SkillTags } from '../components/projects/ProjectCard';
import { Async } from '../components/ui/Async';
import { ButtonLink } from '../components/ui/Button';
import { Panel } from '../components/ui/Panel';
import { StatusBadge } from '../components/ui/StatusBadge';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { projectsService } from '../services/projects.service';
import type { Project } from '../types/marketplace';
import { formatDate, formatInr } from '../utils/format';

function ProjectActions({ project }: { project: Project }) {
  const { status, user } = useAuth();
  const open = project.status === 'OPEN';

  if (status !== 'authenticated' || !user) {
    return (
      <div className="flex flex-col gap-3">
        <ButtonLink to="/login" state={{ from: `/projects/${project.id}` }} variant="accent" size="lg">
          Sign in to send a proposal
        </ButtonLink>
        <p className="text-sm text-mute">
          New to ChainLance?{' '}
          <Link to="/register" className="font-medium text-ink underline underline-offset-4">
            Create a freelancer account
          </Link>
        </p>
      </div>
    );
  }
  if (user.role === 'FREELANCER') {
    return open ? (
      <ButtonLink to={`/app/projects/${project.id}/apply`} variant="accent" size="lg">
        Send a proposal
      </ButtonLink>
    ) : (
      <p className="text-mute">This project is not accepting proposals.</p>
    );
  }
  if (user.role === 'CLIENT' && user.id === project.clientId) {
    return (
      <div className="flex flex-col gap-3">
        <ButtonLink to={`/app/projects/${project.id}/proposals`} variant="accent" size="lg">
          Review proposals
        </ButtonLink>
        <ButtonLink to={`/app/projects/${project.id}/edit`} variant="outline">
          Edit project
        </ButtonLink>
      </div>
    );
  }
  return <p className="text-mute">Only freelancers can send proposals. You are signed in as a {user.role.toLowerCase()}.</p>;
}

export default function ProjectDetailsPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const project = useResource(() => projectsService.get(projectId), [projectId]);
  useDocumentTitle(project.data?.title ?? 'Project');

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <Link to="/projects" className="inline-flex items-center gap-2 text-sm font-medium underline-offset-4 hover:underline">
        <ArrowLeft aria-hidden className="size-4" />
        All projects
      </Link>
      <div className="mt-6">
        <Async state={project} rows={5}>
          {(p) => (
            <div className="grid gap-12 lg:grid-cols-[1.7fr_1fr]">
              <article>
                <div className="flex flex-wrap items-center gap-3 text-sm text-mute">
                  <StatusBadge status={p.status} />
                  <span>{p.category}</span>
                  {p.clientName && <span>posted by {p.clientName}</span>}
                </div>
                <h1 className="mt-4 font-display text-5xl leading-[1.02] sm:text-6xl">{p.title}</h1>
                <div className="mt-6">
                  <SkillTags skills={p.skills} max={20} />
                </div>
                <h2 className="mt-10 text-lg font-semibold">About the work</h2>
                {/* Rendered as text, never as HTML: the description comes from another user. */}
                <p className="mt-3 max-w-2xl whitespace-pre-line leading-relaxed text-ink-soft">{p.description}</p>

                {p.milestones.length > 0 && (
                  <>
                    <h2 className="mt-10 text-lg font-semibold">Client’s milestone plan</h2>
                    <ol className="mt-3 max-w-2xl">
                      {p.milestones.map((m) => (
                        <li key={m.position} className="grid grid-cols-[2rem_1fr_auto] gap-3 border-b border-line py-3">
                          <span className="font-display text-2xl leading-none">{m.position}</span>
                          <span>{m.description}</span>
                          <span className="font-semibold">{formatInr(m.allocationInr)}</span>
                        </li>
                      ))}
                    </ol>
                  </>
                )}
              </article>

              <aside className="lg:sticky lg:top-24 lg:self-start">
                <Panel>
                  <dl className="grid gap-5">
                    <div>
                      <dt className="text-sm text-mute">Budget</dt>
                      <dd className="mt-1 font-display text-5xl leading-none">{formatInr(p.budgetInr)}</dd>
                    </div>
                    <div>
                      <dt className="text-sm text-mute">Deadline</dt>
                      <dd className="mt-1">
                        <DeadlineLabel iso={p.deadline} />
                      </dd>
                    </div>
                    <div>
                      <dt className="text-sm text-mute">Posted</dt>
                      <dd className="mt-1">{formatDate(p.createdAt)}</dd>
                    </div>
                  </dl>
                  <div className="mt-6 border-t border-line pt-6">
                    <ProjectActions project={p} />
                  </div>
                </Panel>
              </aside>
            </div>
          )}
        </Async>
      </div>
    </div>
  );
}
