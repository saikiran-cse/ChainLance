import { useEffect } from 'react';

export const DEFAULT_TITLE = 'ChainLance — Good work deserves trust';

export function useDocumentTitle(title?: string): void {
  useEffect(() => {
    document.title = title ? `${title} — ChainLance` : DEFAULT_TITLE;
  }, [title]);
}
