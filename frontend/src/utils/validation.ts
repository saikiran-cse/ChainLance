/**
 * Client-side validation for usability only. The backend is the final authority for validation and permissions;
 * limits below mirror what the backend DTOs and V1__init.sql enforce today so users get feedback before submitting.
 */
export type FieldErrors<K extends string = string> = Partial<Record<K, string>>;

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
/** DECIMAL(15,2) holds values below 10^13 */
export const MAX_MONEY = 9_999_999_999_999;

export function hasErrors(errors: object): boolean {
  return Object.values(errors).some(Boolean);
}

function byteLength(text: string): number {
  return new TextEncoder().encode(text).length;
}

export function isHttpUrl(text: string): boolean {
  try {
    const url = new URL(text);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

/** Split free text (newlines, commas or spaces) into a clean list of unique entries. */
export function splitList(text: string): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const part of text.split(/[\n,]+/)) {
    const value = part.trim();
    const key = value.toLowerCase();
    if (value && !seen.has(key)) {
      seen.add(key);
      out.push(value);
    }
  }
  return out;
}

/** Parse the project `skills` value, which may arrive as a list or as one comma separated string. */
export function parseSkills(value: unknown): string[] {
  if (Array.isArray(value)) return splitList(value.filter((v): v is string => typeof v === 'string').join(','));
  if (typeof value === 'string') return splitList(value);
  return [];
}

export function validateLogin(v: { email: string; password: string }): FieldErrors<'email' | 'password'> {
  const e: FieldErrors<'email' | 'password'> = {};
  if (!v.email.trim()) e.email = 'Enter your email address.';
  else if (!EMAIL_RE.test(v.email.trim())) e.email = 'Enter a valid email address.';
  if (!v.password) e.password = 'Enter your password.';
  return e;
}

export function validateRegister(v: {
  displayName: string;
  email: string;
  password: string;
  role: string;
}): FieldErrors<'displayName' | 'email' | 'password' | 'role'> {
  const e: FieldErrors<'displayName' | 'email' | 'password' | 'role'> = {};
  if (!v.displayName.trim()) e.displayName = 'Tell us what to call you.';
  else if (v.displayName.trim().length > 100) e.displayName = 'Keep your name under 100 characters.';
  if (!v.email.trim()) e.email = 'Enter your email address.';
  else if (!EMAIL_RE.test(v.email.trim())) e.email = 'Enter a valid email address.';
  else if (v.email.trim().length > 255) e.email = 'That email address is too long.';
  if (v.password.length < 8) e.password = 'Use at least 8 characters.';
  else if (byteLength(v.password) > 72) e.password = 'Passwords can be at most 72 bytes. Try a shorter one.';
  if (v.role !== 'CLIENT' && v.role !== 'FREELANCER') e.role = 'Choose how you will use ChainLance.';
  return e;
}

export function validateProfile(v: { displayName: string; bio: string }): FieldErrors<'displayName' | 'bio'> {
  const e: FieldErrors<'displayName' | 'bio'> = {};
  if (!v.displayName.trim()) e.displayName = 'Display name cannot be empty.';
  else if (v.displayName.trim().length > 100) e.displayName = 'Keep your name under 100 characters.';
  if (v.bio.length > 2000) e.bio = 'Bio can be at most 2000 characters.';
  return e;
}

function parseMoney(text: string): number {
  const n = Number(text.replace(/,/g, '').trim());
  return Number.isFinite(n) ? n : Number.NaN;
}

export function toMoney(text: string): number {
  return parseMoney(text);
}

export interface ProjectFormValues {
  title: string;
  description: string;
  category: string;
  skills: string;
  budget: string;
  deadline: string;
  milestones: Array<{ description: string; allocation: string }>;
}

export const MAX_MILESTONES = 10;

export function validateProject(v: ProjectFormValues, now: Date = new Date()): FieldErrors {
  const e: FieldErrors = {};
  if (!v.title.trim()) e.title = 'Give the project a title.';
  else if (v.title.trim().length > 200) e.title = 'Keep the title under 200 characters.';
  if (!v.description.trim()) e.description = 'Describe the work so freelancers can scope it.';
  if (!v.category.trim()) e.category = 'Choose a category.';
  const skills = splitList(v.skills);
  if (skills.length === 0) e.skills = 'Add at least one required skill.';
  else if (skills.join(', ').length > 500) e.skills = 'That is too many skills. Keep the list under 500 characters.';

  const budget = parseMoney(v.budget);
  if (!v.budget.trim()) e.budget = 'Enter a budget in INR.';
  else if (!(budget > 0)) e.budget = 'Budget must be greater than zero.';
  else if (budget > MAX_MONEY) e.budget = 'That budget is too large.';

  if (v.deadline) {
    const d = new Date(`${v.deadline}T23:59:59`);
    if (Number.isNaN(d.getTime())) e.deadline = 'Enter a valid date.';
    else if (d.getTime() < now.getTime()) e.deadline = 'The deadline must be in the future.';
  }

  if (v.milestones.length > MAX_MILESTONES) e.milestones = `Use at most ${MAX_MILESTONES} milestones.`;
  let allocated = 0;
  v.milestones.forEach((m, i) => {
    const amount = parseMoney(m.allocation);
    if (!m.description.trim()) e[`milestone-${i}-description`] = 'Describe this milestone.';
    else if (m.description.trim().length > 500) e[`milestone-${i}-description`] = 'Keep it under 500 characters.';
    if (!m.allocation.trim()) e[`milestone-${i}-allocation`] = 'Enter an amount.';
    else if (!(amount > 0)) e[`milestone-${i}-allocation`] = 'Must be greater than zero.';
    else allocated += amount;
  });
  if (!e.milestones && budget > 0 && allocated > budget) {
    e.milestones = 'Milestone amounts add up to more than the project budget.';
  }
  return e;
}

export interface ProposalFormValues {
  message: string;
  price: string;
  durationDays: string;
  links: string;
}

export function validateProposal(v: ProposalFormValues): FieldErrors<keyof ProposalFormValues> {
  const e: FieldErrors<keyof ProposalFormValues> = {};
  if (!v.message.trim()) e.message = 'Write a cover letter explaining why you are a fit.';
  const price = parseMoney(v.price);
  if (!v.price.trim()) e.price = 'Enter your proposed price in INR.';
  else if (!(price > 0)) e.price = 'Price must be greater than zero.';
  else if (price > MAX_MONEY) e.price = 'That price is too large.';
  const days = Number(v.durationDays);
  if (!v.durationDays.trim()) e.durationDays = 'Enter the estimated duration in days.';
  else if (!Number.isInteger(days) || days <= 0) e.durationDays = 'Use a whole number of days, at least 1.';
  else if (days > 3650) e.durationDays = 'That duration is too long.';
  const links = splitList(v.links);
  if (links.some((l) => !isHttpUrl(l))) e.links = 'Each portfolio link must start with http:// or https://.';
  else if (links.join(', ').length > 1000) e.links = 'Too many links. Keep the list under 1000 characters.';
  return e;
}

export interface CounterFormValues {
  message: string;
  price: string;
  durationDays: string;
}

export function validateCounter(v: CounterFormValues): FieldErrors<keyof CounterFormValues> {
  const e: FieldErrors<keyof CounterFormValues> = {};
  const hasPrice = v.price.trim() !== '';
  const hasDays = v.durationDays.trim() !== '';
  if (!v.message.trim() && !hasPrice && !hasDays) e.message = 'Write a message or propose new terms.';
  if (hasPrice) {
    const price = parseMoney(v.price);
    if (!(price > 0)) e.price = 'Price must be greater than zero.';
    else if (price > MAX_MONEY) e.price = 'That price is too large.';
  }
  if (hasDays) {
    const days = Number(v.durationDays);
    if (!Number.isInteger(days) || days <= 0) e.durationDays = 'Use a whole number of days, at least 1.';
  }
  return e;
}
