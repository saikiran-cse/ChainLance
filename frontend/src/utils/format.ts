const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inrExact = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const dateFmt = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFmt = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});

function toDate(iso?: string | null): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function formatInr(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  return Number.isInteger(value) ? inrWhole.format(value) : inrExact.format(value);
}

export function formatDate(iso?: string | null): string {
  const d = toDate(iso);
  return d ? dateFmt.format(d) : '—';
}

export function formatDateTime(iso?: string | null): string {
  const d = toDate(iso);
  return d ? dateTimeFmt.format(d) : '—';
}

/** "3 hours ago", "in 5 days". Falls back to the date for anything older than a month. */
export function timeAgo(iso?: string | null, now: number = Date.now()): string {
  const d = toDate(iso);
  if (!d) return '—';
  const diffSec = Math.round((d.getTime() - now) / 1000);
  const abs = Math.abs(diffSec);
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  if (abs < 60) return rtf.format(diffSec, 'second');
  if (abs < 3600) return rtf.format(Math.round(diffSec / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(diffSec / 3600), 'hour');
  if (abs < 86400 * 30) return rtf.format(Math.round(diffSec / 86400), 'day');
  return dateFmt.format(d);
}

/** Whole days from now until the date (negative when past). Null when no valid date. */
export function daysUntil(iso?: string | null, now: number = Date.now()): number | null {
  const d = toDate(iso);
  if (!d) return null;
  return Math.ceil((d.getTime() - now) / 86_400_000);
}

export function shortAddress(address?: string | null): string {
  if (!address) return '—';
  return address.length > 12 ? `${address.slice(0, 6)}…${address.slice(-4)}` : address;
}

/** Convert a wei string (DECIMAL(38,0)) to an ETH string using BigInt, never floating point. */
export function weiToEth(wei?: string | null, maxDecimals = 6): string {
  if (!wei || !/^\d+$/.test(wei)) return '—';
  const value = BigInt(wei);
  const base = 10n ** 18n;
  const whole = value / base;
  const frac = (value % base).toString().padStart(18, '0').slice(0, maxDecimals).replace(/0+$/, '');
  return frac ? `${whole}.${frac}` : `${whole}`;
}

export function greeting(now: Date = new Date()): string {
  const h = now.getHours();
  if (h < 5) return 'Still up';
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

export function initials(name?: string | null): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return (first + last).toUpperCase() || '?';
}

/** "ready_to_ship" / "IN_PROGRESS" -> "In progress" */
export function humanizeEnum(value?: string | null): string {
  if (!value) return '—';
  const text = value.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}
