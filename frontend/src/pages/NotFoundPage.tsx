import { ButtonLink } from '../components/ui/Button';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export default function NotFoundPage() {
  useDocumentTitle('Page not found');
  return (
    <div className="mx-auto max-w-3xl px-4 py-28 sm:px-6">
      <h1 className="font-display text-7xl leading-none sm:text-8xl">Page not found</h1>
      <p className="mt-5 max-w-lg text-lg text-mute">
        The address may have a typo, or the page moved. Everything else is a click away.
      </p>
      <div className="mt-8 flex gap-3">
        <ButtonLink to="/" variant="primary">
          Go home
        </ButtonLink>
        <ButtonLink to="/projects" variant="outline">
          Browse projects
        </ButtonLink>
      </div>
    </div>
  );
}
