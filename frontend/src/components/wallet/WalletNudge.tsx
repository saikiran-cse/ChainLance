import { Link } from 'react-router-dom';
import { Wallet } from 'lucide-react';
import { useResource } from '../../hooks/useResource';
import { walletApi } from '../../services/wallet.service';

/**
 * Reminder shown on dashboards until the backend reports at least one verified wallet.
 * Uses the real GET /api/wallet. If that call fails the nudge stays hidden rather than guessing.
 */
export function WalletNudge() {
  const wallets = useResource(() => walletApi.list(), []);
  if (!wallets.data || wallets.data.length > 0) return null;
  return (
    <div className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-ink bg-lime p-5">
      <div className="flex items-start gap-3">
        <Wallet aria-hidden className="mt-0.5 size-5 shrink-0" />
        <div>
          <p className="font-semibold">Verify a wallet before your first agreement</p>
          <p className="text-sm text-ink-soft">Escrow needs an address the backend has confirmed you control.</p>
        </div>
      </div>
      <Link
        to="/app/wallet"
        className="rounded-md border border-ink bg-ink px-4 py-2 text-sm font-medium text-paper hover:bg-ink-soft"
      >
        Connect wallet
      </Link>
    </div>
  );
}
