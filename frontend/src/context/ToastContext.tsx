import { createContext, useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
import { Check, Info, TriangleAlert, X } from 'lucide-react';
import { cn } from '../utils/cn';

export type ToastTone = 'success' | 'error' | 'info';

interface ToastItem {
  id: number;
  tone: ToastTone;
  message: string;
}

export interface ToastContextValue {
  push(toast: { tone?: ToastTone; message: string }): void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);

const ICONS = { success: Check, error: TriangleAlert, info: Info } as const;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const counter = useRef(0);

  const dismiss = useCallback((id: number) => {
    setItems((current) => current.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (toast: { tone?: ToastTone; message: string }) => {
      counter.current += 1;
      const id = counter.current;
      setItems((current) => [...current.slice(-3), { id, tone: toast.tone ?? 'info', message: toast.message }]);
      window.setTimeout(() => dismiss(id), toast.tone === 'error' ? 8000 : 5000);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ push }), [push]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-stretch gap-2 p-4 sm:inset-x-auto sm:right-0 sm:w-96"
      >
        {items.map((item) => {
          const Icon = ICONS[item.tone];
          return (
            <div
              key={item.id}
              role={item.tone === 'error' ? 'alert' : 'status'}
              className={cn(
                'on-ink pointer-events-auto flex items-start gap-3 rounded-md border border-ink bg-ink px-4 py-3 text-sm text-paper shadow-[4px_4px_0_0_var(--color-lime)] animate-rise',
              )}
            >
              <Icon
                aria-hidden
                className={cn('mt-0.5 size-4 shrink-0', item.tone === 'error' ? 'text-[#ff8a70]' : 'text-lime')}
              />
              <p className="flex-1 leading-snug">{item.message}</p>
              <button
                type="button"
                onClick={() => dismiss(item.id)}
                className="-m-1 rounded-sm p-1 text-paper/70 hover:text-paper"
                aria-label="Dismiss notification"
              >
                <X aria-hidden className="size-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}
