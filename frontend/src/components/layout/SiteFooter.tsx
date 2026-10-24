import { Link } from 'react-router-dom';
import { Logo } from './Logo';

const COLUMNS = [
  {
    title: 'Marketplace',
    links: [
      { to: '/projects', label: 'Browse projects' },
      { to: '/register', label: 'Create an account' },
      { to: '/login', label: 'Sign in' },
    ],
  },
  {
    title: 'Learn',
    links: [
      { to: '/#how', label: 'How ChainLance works' },
      { to: '/#escrow', label: 'How escrow works' },
      { to: '/#milestones', label: 'Why milestones' },
    ],
  },
  {
    title: 'Project status',
    links: [{ to: '/api-status', label: 'Backend progress' }],
  },
];

export function SiteFooter() {
  return (
    <footer className="on-ink mt-24 bg-ink text-paper">
      <div className="mx-auto grid max-w-7xl gap-12 px-4 py-14 sm:px-6 md:grid-cols-[1.4fr_2fr] lg:px-8">
        <div>
          <Logo className="text-paper" />
          <p className="mt-5 max-w-sm text-paper/75">
            Clients and freelancers agree on terms in the open, then pay milestone by milestone through an escrow
            contract on Ethereum Sepolia.
          </p>
        </div>
        <div className="grid grid-cols-2 gap-8 sm:grid-cols-3">
          {COLUMNS.map((col) => (
            <nav key={col.title} aria-label={col.title}>
              <h2 className="text-sm font-semibold text-lime">{col.title}</h2>
              <ul className="mt-4 flex flex-col gap-2.5">
                {col.links.map((l) => (
                  <li key={l.label}>
                    <Link to={l.to} className="text-paper/80 underline-offset-4 hover:text-paper hover:underline">
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>
      </div>
      <div className="border-t border-paper/15">
        <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-6 text-sm text-paper/60 sm:flex-row sm:justify-between sm:px-6 lg:px-8">
          <p>ChainLance is under active development. Features and payments run on a test network.</p>
          <p>Amounts in INR are indicative. Escrow settles in ETH on Sepolia.</p>
        </div>
      </div>
    </footer>
  );
}
