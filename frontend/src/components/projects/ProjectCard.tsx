import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { CalendarClock } from 'lucide-react';
import type { Project } from '../../types/marketplace';
import { daysUntil, formatDate, formatInr, timeAgo } from '../../utils/format';
import { StatusBadge } from '../ui/StatusBadge';

export function SkillTags({ skills, max = 5 }: { skills: string[]; max?: number }) {
  if (skills.length === 0) return null;
  const shown = skills.slice(0, max);
  return (
    <ul className="flex flex-wrap gap-1.5" aria-label="Required skills">
      {shown.map((s) => (
        <li key={s} className="rounded-sm border border-line bg-paper px-2 py-0.5 text-xs text-ink-soft">
          {s}
        </li>
      ))}
      {skills.length > max && <li className="px-1 py-0.5 text-xs text-mute">+{skills.length - max} more</li>}
    </ul>
  );
}

export function DeadlineLabel({ iso }: { iso?: string | null }) {
  if (!iso) return <span className="text-mute">No deadline set</span>;
  const days = daysUntil(iso);
  const note = days == null ? '' : days < 0 ? 'passed' : days === 0 ? 'today' : `in ${days} day${days === 1 ? '' : 's'}`;
  return (
    <span className="inline-flex items-center gap-1.5">
      <CalendarClock aria-hidden className="size-4 text-mute" />
      {formatDate(iso)}
      {note && <span className="text-mute">({note})</span>}
    </span>
  );
}

/** A marketplace listing. The whole card is one link, so keyboard users get one clear target. */
export function ProjectCard({ project }: { project: Project }) {
  return (
    <article className="lift group relative flex flex-col gap-4 rounded-lg border border-line bg-paper-raised p-6">
      <div className="flex items-start justify-between gap-4">
        <p className="text-sm text-mute">
          {project.category}
          {project.clientName ? `, posted by ${project.clientName}` : ''}
        </p>
        <StatusBadge status={project.status} />
      </div>
      <h3 className="font-display text-3xl leading-[1.08]">
        <Link
          to={`/projects/${project.id}`}
          className="after:absolute after:inset-0 after:content-[''] focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-ink"
        >
          {project.title}
        </Link>
      </h3>
      <p className="line-clamp-3 text-ink-soft">{project.description}</p>
      <SkillTags skills={project.skills} />
      <dl className="mt-auto grid grid-cols-2 gap-4 border-t border-line pt-4 text-sm">
        <div>
          <dt className="text-mute">Budget</dt>
          <dd className="mt-0.5 text-lg font-semibold">{formatInr(project.budgetInr)}</dd>
        </div>
        <div>
          <dt className="text-mute">Deadline</dt>
          <dd className="mt-1">
            <DeadlineLabel iso={project.deadline} />
          </dd>
        </div>
      </dl>
      <p className="text-xs text-mute">Posted {timeAgo(project.createdAt)}</p>
    </article>
  );
}

/** Compact row for dashboards and "my projects". */
export function ProjectRow({ project, to, trailing }: { project: Project; to: string; trailing?: ReactNode }) {
  return (
    <li className="grid gap-2 border-b border-line py-4 last:border-b-0 sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-6">
      <div className="min-w-0">
        <Link to={to} className="block truncate font-medium underline-offset-4 hover:underline">
          {project.title}
        </Link>
        <p className="text-sm text-mute">
          {project.category}, posted {timeAgo(project.createdAt)}
        </p>
      </div>
      <p className="font-semibold">{formatInr(project.budgetInr)}</p>
      <div className="flex items-center gap-3">
        <StatusBadge status={project.status} />
        {trailing}
      </div>
    </li>
  );
}
