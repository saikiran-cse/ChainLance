import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { Marquee } from '../components/landing/Marquee';
import { WorkflowDiagram } from '../components/landing/WorkflowDiagram';
import { Reveal } from '../components/motion/Reveal';
import { ButtonLink } from '../components/ui/Button';
import { useAuth } from '../hooks/useAuth';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { endpointCounts } from '../services/endpoints';

const STEPS = [
  {
    title: 'A client posts a project',
    body: 'Scope, budget in INR, a deadline and, if wanted, a milestone plan. Nothing is locked yet.',
  },
  {
    title: 'Freelancers send proposals',
    body: 'Each proposal carries a cover letter, a price, a duration and portfolio links. The client reviews them side by side.',
  },
  {
    title: 'Both sides negotiate in the open',
    body: 'Offers and counteroffers live in one thread with timestamps, so everyone can see how the terms moved.',
  },
  {
    title: 'The agreed terms are frozen',
    body: 'When both parties finalize, the terms become a hashed snapshot. The escrow contract is checked against it.',
  },
  {
    title: 'Funds are released milestone by milestone',
    body: 'The client funds escrow in MetaMask. Each milestone is paid out when it is approved or settled.',
  },
];

const PATHS = [
  {
    title: 'The client approves',
    body: 'The full milestone amount goes to the freelancer. This is the normal path.',
  },
  {
    title: 'Both sides settle',
    body: 'One party proposes a split, the other accepts the exact same amounts, and the contract pays them out.',
  },
  {
    title: 'An admin resolves a dispute',
    body: 'Only the disputed milestone is frozen. The admin can only split that milestone’s own funds.',
  },
];

export default function LandingPage() {
  useDocumentTitle();
  const { status } = useAuth();
  const counts = endpointCounts();

  return (
    <>
      {/* Hero */}
      <section className="mx-auto grid max-w-7xl gap-12 px-4 pb-20 pt-12 sm:px-6 md:pt-20 lg:grid-cols-[1.25fr_1fr] lg:gap-16 lg:px-8">
        <div className="flex flex-col justify-center">
          <h1 className="font-display text-[clamp(3.5rem,11vw,8.5rem)] leading-[0.92] tracking-tight text-ink">
            <span className="hero-line"><span style={{ '--d': '0.05s' } as CSSProperties}>Good work</span></span>
            <span className="hero-line"><span style={{ '--d': '0.18s' } as CSSProperties}>deserves</span></span>
            <span className="hero-line"><span style={{ '--d': '0.31s' } as CSSProperties}>trust.</span></span>
          </h1>
          <div className="grow-x mt-8 h-1.5 w-28 bg-lime" style={{ '--d': '0.6s' } as CSSProperties} aria-hidden />
          <p className="layer-in mt-8 max-w-xl text-lg leading-relaxed text-ink-soft" style={{ '--d': '0.7s' } as CSSProperties}>
            ChainLance connects clients and freelancers through transparent agreements and blockchain-secured milestone
            payments. Terms are written down once, money waits in escrow, and it is released as the work is approved.
          </p>
          <div className="layer-in mt-10 flex flex-wrap gap-3" style={{ '--d': '0.85s' } as CSSProperties}>
            <ButtonLink to="/projects" size="lg" variant="accent">
              Explore projects
            </ButtonLink>
            <ButtonLink to="/#escrow" size="lg" variant="outline">
              How escrow works
            </ButtonLink>
          </div>
          {status !== 'authenticated' && (
            <p className="mt-5 text-sm text-mute">
              New here?{' '}
              <Link to="/register" className="font-medium text-ink underline underline-offset-4">
                Create a free account
              </Link>{' '}
              as a client or a freelancer.
            </p>
          )}
        </div>
        <div className="lg:pt-6">
          <WorkflowDiagram />
        </div>
      </section>

      <Marquee />

      {/* How it works */}
      <section id="how" className="scroll-mt-20 border-y border-line bg-paper-raised">
        <div className="mx-auto grid max-w-7xl gap-12 px-4 py-20 sm:px-6 lg:grid-cols-[1fr_1.4fr] lg:px-8">
          <Reveal>
            <h2 className="font-display text-5xl leading-[1.02] sm:text-6xl">How ChainLance works</h2>
            <p className="mt-5 max-w-sm text-mute">
              Workflow lives in a database the platform controls. Money lives in a contract no one on the platform can
              reach into.
            </p>
          </Reveal>
          <ol className="flex flex-col">
            {STEPS.map((step, i) => (
              <Reveal as="li" key={step.title} delay={i * 0.06} className="group grid grid-cols-[3rem_1fr] gap-4 border-t border-ink py-6 first:border-t-0 first:pt-0">
                <span className="font-display text-4xl leading-none text-ink transition-transform duration-300 group-hover:translate-x-1">
                  {i + 1}
                </span>
                <div>
                  <h3 className="text-xl font-semibold">{step.title}</h3>
                  <p className="mt-1.5 max-w-xl text-mute">{step.body}</p>
                </div>
              </Reveal>
            ))}
          </ol>
        </div>
      </section>

      {/* Milestone payments */}
      <section id="milestones" className="scroll-mt-20 mx-auto max-w-7xl px-4 py-24 sm:px-6 lg:px-8">
        <Reveal>
          <h2 className="max-w-4xl font-display text-5xl leading-[1.02] sm:text-7xl">
            Pay for finished work, in pieces both sides can see.
          </h2>
        </Reveal>
        <div className="mt-14 grid gap-x-12 gap-y-10 md:grid-cols-3">
          <Reveal delay={0} className="border-t-2 border-ink pt-5">
            <h3 className="text-xl font-semibold">Clients risk less</h3>
            <p className="mt-2 text-mute">
              A large project becomes several small payments. Nothing is released for a milestone that has not been
              approved, and a problem on one milestone does not freeze the others.
            </p>
          </Reveal>
          <Reveal delay={0.12} className="border-t-2 border-ink pt-5">
            <h3 className="text-xl font-semibold">Freelancers are paid for what they deliver</h3>
            <p className="mt-2 text-mute">
              Funds are locked in escrow before work starts, so approval is the only thing standing between finished
              work and payment.
            </p>
          </Reveal>
          <Reveal delay={0.24} className="border-t-2 border-ink pt-5">
            <h3 className="text-xl font-semibold">Disagreements have a path</h3>
            <p className="mt-2 text-mute">
              Deadlines, submissions and revisions are tracked per milestone, so a dispute starts from a shared record
              and not from memory.
            </p>
          </Reveal>
        </div>
      </section>

      {/* Escrow */}
      <section id="escrow" className="on-ink scroll-mt-20 bg-ink text-paper">
        <div className="mx-auto max-w-7xl px-4 py-24 sm:px-6 lg:px-8">
          <Reveal>
            <h2 className="max-w-3xl font-display text-5xl leading-[1.02] sm:text-6xl">
              Three ways money can leave escrow. Nothing else.
            </h2>
          </Reveal>
          <p className="mt-5 max-w-2xl text-paper/75">
            Each agreement gets its own escrow contract on Ethereum Sepolia. You sign every transaction yourself in
            MetaMask; ChainLance never holds your keys and has no function that withdraws funds.
          </p>
          <div className="mt-14 grid gap-px overflow-hidden rounded-lg border border-paper/25 bg-paper/25 md:grid-cols-3">
            {PATHS.map((p, i) => (
              <Reveal key={p.title} delay={i * 0.12} className="group relative overflow-hidden bg-ink p-7">
                {/* a lime layer that rises from the bottom edge on hover */}
                <span
                  aria-hidden
                  className="absolute inset-x-0 bottom-0 h-full origin-bottom scale-y-[0.015] bg-lime transition-transform duration-500 ease-out group-hover:scale-y-100"
                />
                <div className="relative transition-colors duration-300 group-hover:text-ink">
                  <h3 className="font-display text-3xl">{p.title}</h3>
                  <p className="mt-3 text-paper/75 transition-colors duration-300 group-hover:text-ink-soft">{p.body}</p>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Honest build status */}
      <section className="mx-auto max-w-7xl px-4 py-24 sm:px-6 lg:px-8">
        <div className="grid items-end gap-8 rounded-lg border border-ink bg-lime p-8 sm:p-12 lg:grid-cols-[1.6fr_1fr]">
          <div>
            <h2 className="font-display text-4xl leading-tight sm:text-5xl">Early, and open about it.</h2>
            <p className="mt-4 max-w-2xl text-ink-soft">
              Accounts, profiles and wallet verification work against the backend today. {counts.pending} planned
              endpoints for projects, proposals, negotiation, agreements and escrow are still being built, and every page
              that depends on one says so instead of showing made-up data.
            </p>
          </div>
          <div className="flex flex-wrap gap-3 lg:justify-end">
            <ButtonLink to="/api-status" variant="primary" size="lg">
              See what is built
            </ButtonLink>
          </div>
        </div>
      </section>
    </>
  );
}
