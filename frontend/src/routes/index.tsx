import { createBrowserRouter, Navigate, type RouteObject } from 'react-router-dom';
import { AppLayout } from '../components/layout/AppLayout';
import { PublicLayout } from '../components/layout/PublicLayout';
import AgreementDetailsPage from '../pages/AgreementDetailsPage';
import AgreementsPage from '../pages/AgreementsPage';
import ApiStatusPage from '../pages/ApiStatusPage';
import BrowseProjectsPage from '../pages/BrowseProjectsPage';
import DashboardPage from '../pages/DashboardPage';
import LandingPage from '../pages/LandingPage';
import LoginPage from '../pages/LoginPage';
import MyProjectsPage from '../pages/MyProjectsPage';
import MyProposalsPage from '../pages/MyProposalsPage';
import NegotiationPage from '../pages/NegotiationPage';
import NotFoundPage from '../pages/NotFoundPage';
import ProfilePage from '../pages/ProfilePage';
import ProjectDetailsPage from '../pages/ProjectDetailsPage';
import ProjectEditorPage from '../pages/ProjectEditorPage';
import ProposalReviewPage from '../pages/ProposalReviewPage';
import RegisterPage from '../pages/RegisterPage';
import SubmitProposalPage from '../pages/SubmitProposalPage';
import WalletPage from '../pages/WalletPage';
import AdminDisputesPage from '../pages/AdminDisputesPage';
import { RedirectIfAuthed, RequireAuth } from './guards';

/**
 * Route map. The role guards only decide what the UI shows; the backend enforces permissions on every request.
 * Later milestone, submission, dispute and escrow pages belong under /app/agreements/:id/... and are added here.
 */
const routes: RouteObject[] = [
  {
    element: <PublicLayout />,
    children: [
      { index: true, element: <LandingPage /> },
      { path: 'projects', element: <BrowseProjectsPage /> },
      { path: 'projects/:id', element: <ProjectDetailsPage /> },
      { path: 'api-status', element: <ApiStatusPage /> },
    ],
  },
  {
    element: <RedirectIfAuthed />,
    children: [
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
    ],
  },
  {
    path: 'app',
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'wallet', element: <WalletPage /> },
          { path: 'agreements', element: <AgreementsPage /> },
          { path: 'agreements/:id', element: <AgreementDetailsPage /> },
          { path: 'proposals/:id/negotiation', element: <NegotiationPage /> },
        ],
      },
    ],
  },
  {
    path: 'app',
    element: <RequireAuth roles={['CLIENT']} />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: 'projects', element: <MyProjectsPage /> },
          { path: 'projects/new', element: <ProjectEditorPage /> },
          { path: 'projects/:id/edit', element: <ProjectEditorPage /> },
          { path: 'projects/:id/proposals', element: <ProposalReviewPage /> },
        ],
      },
    ],
  },
  {
    path: 'app',
    element: <RequireAuth roles={['FREELANCER']} />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: 'proposals', element: <MyProposalsPage /> },
          { path: 'projects/:id/apply', element: <SubmitProposalPage /> },
        ],
      },
    ],
  },
  {
    path: 'app',
    element: <RequireAuth roles={['ADMIN']} />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: 'admin/disputes', element: <AdminDisputesPage /> },
        ],
      },
    ],
  },
  { path: 'dashboard', element: <Navigate to="/app" replace /> },
  {
    element: <PublicLayout />,
    children: [{ path: '*', element: <NotFoundPage /> }],
  },
];

export const router = createBrowserRouter(routes);
