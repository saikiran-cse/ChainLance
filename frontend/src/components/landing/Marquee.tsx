const WORDS = [
  'Project',
  'Proposal',
  'Negotiation',
  'Agreement',
  'Snapshot',
  'Escrow',
  'Milestone',
  'Approval',
  'Settlement',
  'Release',
];

/**
 * A slow ticker of the stages every job moves through. Decorative, so it is hidden from screen readers;
 * the same sequence is explained in text right below it. Pauses on hover and stops for reduced motion.
 */
export function Marquee() {
  const row = (
    <ul className="flex shrink-0 items-center">
      {WORDS.map((w) => (
        <li key={w} className="flex items-center">
          <span className="px-8 font-display text-4xl leading-none text-ink sm:text-5xl">{w}</span>
          <span aria-hidden className="size-3 rotate-45 border border-ink bg-ink" />
        </li>
      ))}
    </ul>
  );
  return (
    <div aria-hidden className="marquee overflow-hidden border-y border-ink bg-lime py-5">
      <div className="marquee-track">
        {row}
        {row}
      </div>
    </div>
  );
}
