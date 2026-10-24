/**
 * SAMPLE DATA FOR VISUAL DEVELOPMENT ONLY.
 * Nothing here comes from the ChainLance backend. It is used only when VITE_USE_MOCKS=true, and the UI shows a
 * persistent "sample data" banner while it is active. State lives in memory and resets on every page reload.
 * All people and businesses below are invented.
 */
import { getServiceIdentity, type ServiceIdentity } from '../services/identity';
import type {
  Agreement,
  MilestonePlanItem,
  NegotiationMessage,
  Project,
  Proposal,
} from '../types/marketplace';
import { AppError } from '../utils/errors';

export const SAMPLE_CLIENTS = {
  tidewater: { id: 9001, name: 'Tidewater Coffee Co.' },
  kiln: { id: 9002, name: 'Kiln & Co. Ceramics' },
  northfield: { id: 9003, name: 'Northfield Health' },
  paperplane: { id: 9004, name: 'Paperplane Games' },
};

export const SAMPLE_FREELANCERS = {
  ananya: { id: 9101, name: 'Ananya Rao' },
  dev: { id: 9102, name: 'Dev Malhotra' },
  okra: { id: 9103, name: 'Studio Okra' },
};

interface MockState {
  seededFor: number | null;
  nextId: number;
  projects: Project[];
  proposals: Proposal[];
  messages: NegotiationMessage[];
  agreements: Agreement[];
}

export const state: MockState = {
  seededFor: null,
  nextId: 1000,
  projects: [],
  proposals: [],
  messages: [],
  agreements: [],
};

export function nextId(): number {
  state.nextId += 1;
  return state.nextId;
}

export function iso(daysFromNow: number, hoursOffset = 0): string {
  return new Date(Date.now() + daysFromNow * 86_400_000 + hoursOffset * 3_600_000).toISOString();
}

export function delay(ms = 220): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Anonymous visitors can browse the sample marketplace without any signed-in identity. */
export function ensureAnonymousSeed(): void {
  if (state.projects.length === 0) state.projects = seedMarketplace();
}

export function requireIdentity(): ServiceIdentity {
  const me = getServiceIdentity();
  if (!me) throw new AppError('You need to sign in to do that.', { kind: 'unauthorized', status: 401 });
  ensureSeed(me);
  return me;
}

export function notFound(what: string): AppError {
  return new AppError(`${what} was not found.`, { kind: 'not-found', status: 404, code: 'SAMPLE_NOT_FOUND' });
}

export function forbidden(message = 'You do not have permission to do that.'): AppError {
  return new AppError(message, { kind: 'forbidden', status: 403, code: 'FORBIDDEN' });
}

export function conflict(message: string, code: string): AppError {
  return new AppError(message, { kind: 'conflict', status: 409, code });
}

function plan(items: Array<[string, number]>): MilestonePlanItem[] {
  return items.map(([description, allocationInr], i) => ({ position: i + 1, description, allocationInr }));
}

function project(p: Partial<Project> & Pick<Project, 'id' | 'clientId' | 'clientName' | 'title' | 'budgetInr'>): Project {
  return {
    description: '',
    category: 'Web development',
    skills: [],
    deadline: null,
    status: 'OPEN',
    milestones: [],
    createdAt: iso(-3),
    ...p,
  };
}

function message(
  proposalId: number,
  sender: { id: number; name: string },
  text: string,
  at: string,
  terms?: { priceInr?: number; durationDays?: number },
): NegotiationMessage {
  return {
    id: nextId(),
    proposalId,
    senderId: sender.id,
    senderName: sender.name,
    message: text,
    proposedTerms: terms ?? null,
    createdAt: at,
  };
}

/** Sample marketplace listings from invented clients. */
function seedMarketplace(): Project[] {
  const c = SAMPLE_CLIENTS;
  return [
    project({
      id: 1,
      clientId: c.tidewater.id,
      clientName: c.tidewater.name,
      title: 'Online ordering site for a neighbourhood coffee roaster',
      description:
        'We roast small batches and sell through a basic page today. We need a fast, mobile-first ordering site with subscriptions, a pickup scheduler and simple stock management. Design files exist; we need the build and a clean handover.',
      category: 'Web development',
      skills: ['React', 'Node.js', 'Payments', 'PostgreSQL'],
      budgetInr: 180000,
      deadline: iso(75),
      milestones: plan([
        ['Catalogue, cart and checkout', 70000],
        ['Subscriptions and pickup scheduler', 70000],
        ['Admin tools, QA and handover', 40000],
      ]),
      createdAt: iso(-2),
    }),
    project({
      id: 2,
      clientId: c.kiln.id,
      clientName: c.kiln.name,
      title: 'Brand identity and packaging system for a ceramics studio',
      description:
        'A small studio with a growing wholesale line. We want a distinctive logo, colour and type system, and a packaging kit (box, tissue, insert card) that can be reordered from any printer.',
      category: 'Design & branding',
      skills: ['Brand identity', 'Packaging', 'Illustrator'],
      budgetInr: 95000,
      deadline: iso(45),
      createdAt: iso(-5),
    }),
    project({
      id: 3,
      clientId: c.northfield.id,
      clientName: c.northfield.name,
      title: 'Patient reminder app: Android prototype to pilot',
      description:
        'Clinic staff need a lightweight Android app that sends appointment reminders and collects a one-tap confirmation. Offline-tolerant, no patient data stored on the device. Pilot with two clinics.',
      category: 'Mobile apps',
      skills: ['Kotlin', 'Android', 'Firebase'],
      budgetInr: 240000,
      deadline: iso(90),
      milestones: plan([
        ['Interaction prototype and clinic feedback', 60000],
        ['Reminder engine and confirmations', 110000],
        ['Pilot build, fixes and documentation', 70000],
      ]),
      createdAt: iso(-8),
    }),
    project({
      id: 4,
      clientId: c.paperplane.id,
      clientName: c.paperplane.name,
      title: 'Escrow-aware payout contract review and test plan',
      description:
        'We have a Solidity contract that splits payouts between collaborators. We need a careful read-through, a written test plan and a set of Hardhat tests covering the edge cases. No deployment work.',
      category: 'Smart contracts',
      skills: ['Solidity', 'Hardhat', 'Testing'],
      budgetInr: 120000,
      deadline: iso(30),
      createdAt: iso(-1),
    }),
    project({
      id: 5,
      clientId: c.tidewater.id,
      clientName: c.tidewater.name,
      title: 'Product photography and short reels for a seasonal blend',
      description:
        'Six hero photographs and three vertical reels for a seasonal launch. Shoot at our roastery, delivery within two weeks of the shoot date.',
      category: 'Video & audio',
      skills: ['Photography', 'Video editing', 'Colour grading'],
      budgetInr: 55000,
      deadline: iso(21),
      createdAt: iso(-6),
    }),
    project({
      id: 6,
      clientId: c.kiln.id,
      clientName: c.kiln.name,
      title: 'Wholesale catalogue copywriting',
      description:
        'Rewrite descriptions for 60 products and write a short brand introduction for our wholesale catalogue. Tone: warm, precise, no hype.',
      category: 'Writing & content',
      skills: ['Copywriting', 'Editing'],
      budgetInr: 38000,
      deadline: iso(28),
      createdAt: iso(-10),
    }),
    project({
      id: 7,
      clientId: c.northfield.id,
      clientName: c.northfield.name,
      title: 'Clinic scheduling data cleanup and dashboard',
      description:
        'Clean two years of exported scheduling data and build a simple dashboard of no-shows by clinic and weekday. Data is de-identified.',
      category: 'Data & AI',
      skills: ['Python', 'SQL', 'Data visualisation'],
      budgetInr: 85000,
      deadline: iso(40),
      createdAt: iso(-4),
    }),
  ];
}

function finalizeAgreementFor(
  proposal: Proposal,
  proj: Project,
  price: number,
  durationDays: number,
  flags: { clientFinalized?: boolean; freelancerFinalized?: boolean } = {},
): Agreement {
  const scale = proj.budgetInr > 0 ? price / proj.budgetInr : 1;
  const milestones =
    proj.milestones.length > 0
      ? proj.milestones.map((m) => ({
          position: m.position,
          description: m.description,
          amountInr: Math.round(m.allocationInr * scale),
        }))
      : [{ position: 1, description: 'Complete delivery as described in the project brief', amountInr: price }];
  const deadline = iso(durationDays);
  return {
    id: nextId(),
    proposalId: proposal.id,
    projectId: proj.id,
    projectTitle: proj.title,
    projectDescription: proj.description,
    clientId: proj.clientId,
    clientName: proj.clientName,
    freelancerId: proposal.freelancerId,
    freelancerName: proposal.freelancerName,
    totalWei: null,
    priceInr: price,
    deadline,
    status: 'DRAFT',
    clientFinalizedAt: flags.clientFinalized ? iso(-1) : null,
    freelancerFinalizedAt: flags.freelancerFinalized ? iso(-1) : null,
    snapshot: {
      projectTitle: proj.title,
      clientName: proj.clientName ?? null,
      freelancerName: proposal.freelancerName ?? null,
      priceInr: price,
      durationDays,
      deadline,
      milestones,
    },
    snapshotHash: null,
    clientWallet: null,
    freelancerWallet: null,
    escrowAddress: null,
    chainId: null,
    milestones,
    createdAt: iso(-1),
  };
}

export { finalizeAgreementFor as buildAgreement };

/** Seeds sample data once per signed-in identity, so every role has something to look at. */
export function ensureSeed(me: ServiceIdentity): void {
  if (state.seededFor === me.id) return;
  state.seededFor = me.id;
  state.projects = seedMarketplace();
  state.proposals = [];
  state.messages = [];
  state.agreements = [];

  const f = SAMPLE_FREELANCERS;

  if (me.role === 'CLIENT') {
    const a = project({
      id: nextId(),
      clientId: me.id,
      clientName: me.displayName,
      title: 'Storefront refresh and brand toolkit',
      description:
        'Refresh our storefront pages and produce a small brand toolkit (colour, type, photography notes) our team can apply ourselves.',
      category: 'Design & branding',
      skills: ['Brand identity', 'Figma', 'Web design'],
      budgetInr: 95000,
      deadline: iso(50),
      milestones: plan([
        ['Audit and direction', 25000],
        ['Storefront page designs', 45000],
        ['Toolkit and handover', 25000],
      ]),
      createdAt: iso(-6),
    });
    const b = project({
      id: nextId(),
      clientId: me.id,
      clientName: me.displayName,
      title: 'Onboarding flow redesign for the mobile app',
      description: 'Rework the first-run experience. Not published yet: still deciding on scope.',
      category: 'Mobile apps',
      skills: ['UX design', 'Prototyping'],
      budgetInr: 60000,
      deadline: iso(35),
      status: 'DRAFT',
      createdAt: iso(-1),
    });
    const c = project({
      id: nextId(),
      clientId: me.id,
      clientName: me.displayName,
      title: 'Documentation for the payout contract',
      description: 'Plain-language docs and diagrams for an existing contract.',
      category: 'Writing & content',
      skills: ['Technical writing', 'Diagrams'],
      budgetInr: 42000,
      deadline: iso(25),
      status: 'IN_PROGRESS',
      milestones: plan([
        ['Outline and diagrams', 18000],
        ['Full draft and review', 24000],
      ]),
      createdAt: iso(-14),
    });
    state.projects.push(a, b, c);

    const p1: Proposal = {
      id: nextId(),
      projectId: a.id,
      projectTitle: a.title,
      freelancerId: f.ananya.id,
      freelancerName: f.ananya.name,
      message:
        'I have rebuilt storefront pages for three small food and craft brands this year. I would start with a short audit of your current pages, then design the three highest-traffic ones and package the rest as a toolkit your team can apply.',
      priceInr: 88000,
      durationDays: 28,
      portfolioLinks: ['https://example.com/ananya-portfolio'],
      status: 'SUBMITTED',
      createdAt: iso(-2),
    };
    const p2: Proposal = {
      id: nextId(),
      projectId: a.id,
      projectTitle: a.title,
      freelancerId: f.dev.id,
      freelancerName: f.dev.name,
      message:
        'Brand and web design are what I do full time. I can deliver all three milestones in four weeks and keep the toolkit in Figma so it stays editable.',
      priceInr: 92000,
      durationDays: 28,
      portfolioLinks: ['https://example.com/dev-work', 'https://example.com/dev-case-study'],
      status: 'SHORTLISTED',
      createdAt: iso(-3),
    };
    const p3: Proposal = {
      id: nextId(),
      projectId: c.id,
      projectTitle: c.title,
      freelancerId: f.okra.id,
      freelancerName: f.okra.name,
      message: 'We write developer documentation for small teams and can start this week.',
      priceInr: 42000,
      durationDays: 21,
      portfolioLinks: [],
      status: 'ACCEPTED',
      createdAt: iso(-12),
    };
    state.proposals.push(p1, p2, p3);

    state.messages.push(
      message(p2.id, f.dev, 'Thanks for shortlisting. Happy to talk through scope.', iso(-2, -4)),
      message(
        p2.id,
        { id: me.id, name: me.displayName },
        'We like the portfolio. Could you do it for a little less if we pay milestone one up front?',
        iso(-1, -6),
        { priceInr: 85000, durationDays: 30 },
      ),
      message(
        p2.id,
        f.dev,
        'I can meet you in the middle on price. Timeline stays at four weeks.',
        iso(-1, -1),
        { priceInr: 89000, durationDays: 28 },
      ),
    );
    state.agreements.push(finalizeAgreementFor(p3, c, 42000, 21, { clientFinalized: true }));
  }

  if (me.role === 'FREELANCER') {
    const mine = (projectId: number): Project => {
      const found = state.projects.find((p) => p.id === projectId);
      if (!found) throw notFound('Sample project');
      return found;
    };
    const s1 = mine(2);
    const s2 = mine(3);
    const s3 = mine(5);

    const q1: Proposal = {
      id: nextId(),
      projectId: s1.id,
      projectTitle: s1.title,
      freelancerId: me.id,
      freelancerName: me.displayName,
      message: 'I have shipped packaging systems for small studios and can handle print-ready files end to end.',
      priceInr: 90000,
      durationDays: 35,
      portfolioLinks: [],
      status: 'SUBMITTED',
      createdAt: iso(-2),
    };
    const q2: Proposal = {
      id: nextId(),
      projectId: s2.id,
      projectTitle: s2.title,
      freelancerId: me.id,
      freelancerName: me.displayName,
      message: 'I have built offline-tolerant Android apps before and can deliver the pilot build in three months.',
      priceInr: 235000,
      durationDays: 80,
      portfolioLinks: ['https://example.com/android-work'],
      status: 'SHORTLISTED',
      createdAt: iso(-5),
    };
    const q3: Proposal = {
      id: nextId(),
      projectId: s3.id,
      projectTitle: s3.title,
      freelancerId: me.id,
      freelancerName: me.displayName,
      message: 'I can cover the shoot and edit all three reels within the two-week window.',
      priceInr: 52000,
      durationDays: 18,
      portfolioLinks: [],
      status: 'ACCEPTED',
      createdAt: iso(-9),
    };
    state.proposals.push(q1, q2, q3);

    const client = SAMPLE_CLIENTS.northfield;
    state.messages.push(
      message(q2.id, { id: client.id, name: client.name }, 'We would like to proceed. Can the pilot build come in closer to our budget?', iso(-2, -3), {
        priceInr: 220000,
        durationDays: 90,
      }),
    );
    state.agreements.push(finalizeAgreementFor(q3, s3, 52000, 18, { clientFinalized: true }));
  }
}
