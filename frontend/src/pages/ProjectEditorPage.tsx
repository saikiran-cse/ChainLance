import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { ProjectForm } from '../components/projects/ProjectForm';
import { Async } from '../components/ui/Async';
import { PageHeader } from '../components/ui/Panel';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useResource } from '../hooks/useResource';
import { useToast } from '../hooks/useToast';
import { projectsService } from '../services/projects.service';
import type { ProjectInput } from '../types/marketplace';

/** Create (/app/projects/new) and edit (/app/projects/:id/edit) share one form. */
export default function ProjectEditorPage() {
  const { id } = useParams();
  const editingId = id ? Number(id) : null;
  useDocumentTitle(editingId ? 'Edit project' : 'New project');
  const navigate = useNavigate();
  const toast = useToast();

  const existing = useResource(async () => (editingId ? projectsService.get(editingId) : undefined), [editingId]);

  async function onCreate(input: ProjectInput, publish: boolean) {
    const created = await projectsService.create(input, publish);
    toast.push({ tone: 'success', message: publish ? 'Project published.' : 'Draft saved.' });
    navigate(created.status === 'OPEN' ? `/projects/${created.id}` : '/app/projects');
  }

  async function onUpdate(projectId: number, wasDraft: boolean, input: ProjectInput, publish: boolean) {
    const saved = await projectsService.update(projectId, input);
    if (publish && wasDraft) {
      await projectsService.publish(saved.id);
      toast.push({ tone: 'success', message: 'Project published.' });
      navigate(`/projects/${saved.id}`);
      return;
    }
    toast.push({ tone: 'success', message: 'Changes saved.' });
    navigate('/app/projects');
  }

  return (
    <div className="flex flex-col gap-8">
      <Link to="/app/projects" className="inline-flex items-center gap-2 text-sm font-medium underline-offset-4 hover:underline">
        <ArrowLeft aria-hidden className="size-4" />
        My projects
      </Link>
      <PageHeader title={editingId ? 'Edit project' : 'Post a project'}>
        {editingId
          ? 'Changes are validated by the backend when you save.'
          : 'Describe the work clearly. Freelancers will price it from what you write here.'}
      </PageHeader>
      {editingId ? (
        <Async state={existing} rows={5}>
          {(project) =>
            project ? (
              <ProjectForm
                initial={project}
                onSubmit={(input, publish) => onUpdate(project.id, project.status === 'DRAFT', input, publish)}
              />
            ) : null
          }
        </Async>
      ) : (
        <ProjectForm onSubmit={onCreate} />
      )}
    </div>
  );
}
