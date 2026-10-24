import { useCallback, useEffect, useState } from 'react';
import { AppError, toAppError } from '../utils/errors';

export interface ResourceState<T> {
  data: T | undefined;
  error: AppError | null;
  /** True while a request is in flight, including reloads that keep showing the previous data. */
  loading: boolean;
  reload(): void;
}

/**
 * Load something when the component mounts or `deps` change.
 * Stale responses are ignored, and previous data stays visible while a reload is running.
 */
export function useResource<T>(loader: () => Promise<T>, deps: ReadonlyArray<unknown>): ResourceState<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<AppError | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    loader()
      .then((result) => {
        if (cancelled) return;
        setData(result);
        setLoading(false);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(toAppError(e));
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // `loader` is intentionally excluded: callers pass an inline function and list what it depends on in `deps`.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((n) => n + 1), []);

  return { data, error, loading, reload };
}
