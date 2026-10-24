import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { ButtonLink } from '../components/ui/Button';
import { PageHeader } from '../components/ui/Panel';
import { EmptyState } from '../components/ui/States';
import ClientDashboardPage from './ClientDashboardPage';
import FreelancerDashboardPage from './FreelancerDashboardPage';

/** /app picks the dashboard that matches the signed-in role. */
export default function DashboardPage() {
  const { user } = useAuth();
  useDocumentTitle('Dashboard');
  if (user?.role === 'CLIENT') return <ClientDashboardPage />;
  if (user?.role === 'FREELANCER') return <FreelancerDashboardPage />;
  return (
    <div className="flex flex-col gap-8">
      <PageHeader title="Administration">
        You are signed in as an administrator. The dispute review queue arrives with the admin endpoints.
      </PageHeader>
      <EmptyState
        title="No admin tools yet"
        action={
          <ButtonLink to="/api-status" variant="primary">
            See backend progress
          </ButtonLink>
        }
      >
        GET /api/admin/disputes is reserved for administrators but not built yet.
      </EmptyState>
    </div>
  );
}
