import type { ReactNode } from 'react';
import { Logo } from './Logo';

/** Two-column frame for sign-in and registration: a quiet ink panel beside the form. */
export function AuthShell({ title, intro, children }: { title: string; intro: string; children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1fr_1.1fr]">
      <aside className="on-ink hidden flex-col justify-between bg-ink p-12 text-paper lg:flex">
        <Logo className="text-paper" />
        <div>
          <p className="font-display text-6xl leading-[0.98]">Good work deserves trust.</p>
          <div className="mt-8 h-1.5 w-20 bg-lime" aria-hidden />
          <p className="mt-6 max-w-sm text-paper/75">
            Agree on terms in the open. Keep the money in escrow. Release it as each milestone is approved.
          </p>
        </div>
        <p className="text-sm text-paper/55">Runs on Ethereum Sepolia. Test network only.</p>
      </aside>
      <main id="main" className="flex flex-col px-4 py-8 sm:px-10 lg:px-16">
        <div className="lg:hidden">
          <Logo />
        </div>
        <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center py-10">
          <h1 className="font-display text-5xl leading-none">{title}</h1>
          <p className="mt-3 text-mute">{intro}</p>
          <div className="mt-8">{children}</div>
        </div>
      </main>
    </div>
  );
}
