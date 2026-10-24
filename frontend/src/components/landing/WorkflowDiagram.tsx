import { useRef, type CSSProperties, type PointerEvent, type ReactNode } from 'react';
import { cn } from '../../utils/cn';

/**
 * Illustration of the product's flow: project, agreement, escrow. The wording is generic on purpose.
 * It shows how the pieces relate, not live data, and it says so in its caption.
 *
 * Motion: the three slabs assemble in order, the connectors draw between them, the first escrow milestone fills lime,
 * and on a mouse the layers drift by different amounts so the stack reads as depth. Touch and reduced-motion get the
 * finished picture with no drift.
 */
const vars = (v: Record<string, string | number>) => v as CSSProperties;

function Stage({
  title,
  who,
  children,
  className,
  z,
  delay,
  fromX,
  layerClass,
}: {
  title: string;
  who: string;
  children: ReactNode;
  className?: string;
  /** parallax strength in px at the edge of the figure */
  z: number;
  delay: number;
  /** horizontal start offset for the entrance */
  fromX: number;
  /** solid colour of the layer peeking out underneath */
  layerClass: string;
}) {
  return (
    <div className="depth" style={vars({ '--z': z })}>
      <div className="layer-in relative" style={vars({ '--d': `${delay}s`, '--fx': `${fromX}px` })}>
        {/* the layer underneath */}
        <div aria-hidden className={cn('absolute inset-0 translate-x-2.5 translate-y-2.5 rounded-lg border border-ink', layerClass)} />
        <div className={cn('relative rounded-lg border border-ink bg-paper-raised p-5', className)}>
          <div className="flex items-baseline justify-between gap-4">
            <h3 className="font-display text-2xl leading-none">{title}</h3>
            <span className="text-sm text-mute">{who}</span>
          </div>
          <div className="mt-4">{children}</div>
        </div>
      </div>
    </div>
  );
}

function Connector({ label, delay }: { label: string; delay: number }) {
  return (
    <div className="flex items-stretch gap-3 py-0.5 pl-8" aria-hidden>
      <div className="grow-y w-px bg-ink" style={vars({ '--d': `${delay}s`, minHeight: '2.5rem' })} />
      <span
        className="layer-in self-center rounded-sm border border-ink bg-lime px-2 py-0.5 text-xs font-medium text-ink"
        style={vars({ '--d': `${delay + 0.25}s`, '--fx': '-12px' })}
      >
        {label}
      </span>
    </div>
  );
}

function Line({ w, delay }: { w: string; delay: number }) {
  return (
    <div className="h-2 overflow-hidden rounded-sm bg-stone" style={{ width: w }}>
      <div className="grow-x h-full w-full bg-stone-deep" style={vars({ '--d': `${delay}s` })} />
    </div>
  );
}

export function WorkflowDiagram() {
  const ref = useRef<HTMLElement | null>(null);

  function onMove(e: PointerEvent<HTMLElement>) {
    if (e.pointerType !== 'mouse' || !ref.current) return;
    const r = ref.current.getBoundingClientRect();
    // -0.5 .. 0.5 across the figure; each layer multiplies this by its own --z
    ref.current.style.setProperty('--mx', String((e.clientX - r.left) / r.width - 0.5));
    ref.current.style.setProperty('--my', String((e.clientY - r.top) / r.height - 0.5));
  }
  function onLeave() {
    ref.current?.style.setProperty('--mx', '0');
    ref.current?.style.setProperty('--my', '0');
  }

  return (
    <figure ref={ref} className="w-full pb-3 pr-3" onPointerMove={onMove} onPointerLeave={onLeave}>
      <div className="flex flex-col">
        <Stage title="Project" who="Posted by the client" z={-10} delay={0.55} fromX={-40} layerClass="bg-stone-deep">
          <div className="flex flex-col gap-2.5">
            <Line w="82%" delay={1.1} />
            <Line w="64%" delay={1.25} />
          </div>
          <div className="mt-4 flex flex-wrap gap-2 text-xs">
            <span className="rounded-sm border border-line px-2 py-1">Budget in INR</span>
            <span className="rounded-sm border border-line px-2 py-1">Deadline</span>
            <span className="rounded-sm border border-line px-2 py-1">Milestone plan</span>
          </div>
        </Stage>

        <Connector label="proposal and negotiation" delay={1.0} />

        <Stage title="Agreement" who="Accepted by both sides" z={6} delay={1.15} fromX={40} layerClass="bg-lime">
          <div className="grid grid-cols-[auto_1fr] items-center gap-x-4 gap-y-2.5 text-sm">
            <span className="text-mute">Price</span>
            <Line w="38%" delay={1.7} />
            <span className="text-mute">Deadline</span>
            <Line w="30%" delay={1.8} />
            <span className="text-mute">Scope</span>
            <Line w="70%" delay={1.9} />
          </div>
          <p className="mt-4 border-t border-line pt-3 text-sm text-mute">
            The agreed terms are frozen into a snapshot with a hash. Neither side can quietly change them later.
          </p>
        </Stage>

        <Connector label="funded on Sepolia" delay={1.65} />

        <Stage
          title="Escrow"
          who="One contract per agreement"
          className="bg-ink text-paper"
          z={18}
          delay={1.8}
          fromX={-30}
          layerClass="bg-stone-deep"
        >
          <div className="on-ink">
            <div className="grid grid-cols-3 gap-2" role="img" aria-label="Three milestones: one released, one awaiting approval, one locked">
              <div className="relative overflow-hidden rounded-sm border border-lime p-3">
                <span aria-hidden className="grow-x absolute inset-0 bg-lime" style={vars({ '--d': '2.7s' })} />
                <div className="ink-on relative" style={vars({ '--d': '2.9s' })}>
                  <p className="text-xs font-medium">Milestone 1</p>
                  <p className="mt-3 text-sm font-semibold">Released</p>
                </div>
              </div>
              <div className="rounded-sm border border-lime p-3">
                <p className="text-xs font-medium text-paper/80">Milestone 2</p>
                <p className="mt-3 flex items-center gap-2 text-sm font-semibold">
                  <span aria-hidden className="breathe size-2 shrink-0 rounded-full bg-lime" />
                  Awaiting approval
                </p>
              </div>
              <div className="rounded-sm border border-dashed border-paper/40 p-3 text-paper/70">
                <p className="text-xs font-medium">Milestone 3</p>
                <p className="mt-3 text-sm font-semibold">Locked</p>
              </div>
            </div>
            <p className="mt-4 text-sm text-paper/75">
              Funds move only when the client approves, both sides settle, or an admin resolves a dispute.
            </p>
          </div>
        </Stage>
      </div>
      <figcaption className="mt-5 text-sm text-mute">An illustration of the flow, not live data.</figcaption>
    </figure>
  );
}
