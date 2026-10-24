import { useEffect, useState } from 'react';
import { Search, X } from 'lucide-react';
import { ProjectCard } from '../components/projects/ProjectCard';
import { Async } from '../components/ui/Async';
import { Button } from '../components/ui/Button';
import { SelectField, TextField } from '../components/ui/Field';
import { EmptyState } from '../components/ui/States';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { projectsService } from '../services/projects.service';
import { CATEGORIES } from '../utils/categories';

/** Waits for the user to stop typing before the value is used for a request. */
function useDebounced<T>(value: T, ms = 350): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(value), ms);
    return () => window.clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

export default function BrowseProjectsPage() {
  useDocumentTitle('Browse projects');
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('');
  const [skill, setSkill] = useState('');
  const dq = useDebounced(q);
  const dskill = useDebounced(skill);

  const projects = useResource(
    () => projectsService.list({ q: dq, category: category || undefined, skill: dskill.trim() || undefined }),
    [dq, category, dskill],
  );
  const filtered = !!(q || category || skill);

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
      <header className="max-w-3xl">
        <h1 className="font-display text-6xl leading-[0.98] sm:text-7xl">Open projects</h1>
        <p className="mt-4 text-lg text-mute">
          Every project here has a budget, a deadline and a client ready to read proposals.
        </p>
      </header>

      <form
        role="search"
        aria-label="Filter projects"
        onSubmit={(e) => e.preventDefault()}
        className="mt-10 grid gap-4 border-y border-line py-6 md:grid-cols-[2fr_1fr_1fr_auto] md:items-end"
      >
        <div className="relative">
          <TextField
            label="Search"
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Title, description or skill"
            className="pl-10"
          />
          <Search aria-hidden className="pointer-events-none absolute bottom-3 left-3 size-5 text-mute" />
        </div>
        <SelectField label="Category" value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">All categories</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </SelectField>
        <TextField label="Skill" value={skill} onChange={(e) => setSkill(e.target.value)} placeholder="React" />
        <Button
          variant="ghost"
          icon={<X aria-hidden className="size-4" />}
          disabled={!filtered}
          onClick={() => {
            setQ('');
            setCategory('');
            setSkill('');
          }}
          className="h-11"
        >
          Clear
        </Button>
      </form>

      <div className="mt-8" aria-live="polite">
        <Async state={projects} rows={4}>
          {(items) =>
            items.length === 0 ? (
              <EmptyState
                title={filtered ? 'No projects match those filters' : 'No open projects yet'}
                action={
                  filtered ? (
                    <Button
                      variant="outline"
                      onClick={() => {
                        setQ('');
                        setCategory('');
                        setSkill('');
                      }}
                    >
                      Clear filters
                    </Button>
                  ) : undefined
                }
              >
                {filtered ? 'Try fewer words, or pick a different category.' : 'New projects appear here as soon as clients publish them.'}
              </EmptyState>
            ) : (
              <>
                <p className="mb-5 text-sm text-mute">
                  {items.length} {items.length === 1 ? 'project' : 'projects'}
                </p>
                <div className="stagger grid gap-6 md:grid-cols-2 xl:grid-cols-3">
                  {items.map((p) => (
                    <ProjectCard key={p.id} project={p} />
                  ))}
                </div>
              </>
            )
          }
        </Async>
      </div>
    </div>
  );
}
