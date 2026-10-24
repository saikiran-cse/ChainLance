import type { ReactNode } from 'react';
import type { ResourceState } from '../../hooks/useResource';
import { ErrorState, SkeletonRows } from './States';

/**
 * Renders loading, error and success for one useResource() result, so every page handles all three the same way.
 * Previous data stays on screen during a reload instead of flashing back to a skeleton.
 */
export function Async<T>({
  state,
  rows = 3,
  children,
}: {
  state: ResourceState<T>;
  rows?: number;
  children: (data: T) => ReactNode;
}) {
  if (state.data !== undefined && !state.error) return <>{children(state.data)}</>;
  if (state.error) return <ErrorState error={state.error} onRetry={state.reload} />;
  return <SkeletonRows rows={rows} />;
}
