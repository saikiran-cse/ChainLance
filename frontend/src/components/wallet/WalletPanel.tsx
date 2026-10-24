import { useState } from 'react';
import { BadgeCheck, CircleDashed, ShieldAlert, Unplug } from 'lucide-react';
import { env } from '../../config/env';
import { useResource } from '../../hooks/useResource';
import { useToast } from '../../hooks/useToast';
import { useWallet } from '../../hooks/useWallet';
import { metamask, networkName, walletApi } from '../../services/wallet.service';
import { cn } from '../../utils/cn';
import { toAppError } from '../../utils/errors';
import { formatDateTime, shortAddress } from '../../utils/format';
import { Async } from '../ui/Async';
import { Button } from '../ui/Button';
import { Panel } from '../ui/Panel';
import { EmptyState } from '../ui/States';

const sameAddress = (a?: string | null, b?: string | null) => !!a && !!b && a.toLowerCase() === b.toLowerCase();

/**
 * Connect MetaMask, then prove ownership with a signature the backend checks.
 * "Connected" (MetaMask shares an address) and "verified" (the backend recovered the signer and stored the wallet)
 * are shown as two different things. The UI marks a wallet verified only when GET /api/wallet lists it.
 */
export function WalletPanel() {
  const wallet = useWallet();
  const toast = useToast();
  const linked = useResource(() => walletApi.list(), []);
  const [verifying, setVerifying] = useState(false);
  const [verifyError, setVerifyError] = useState<string | null>(null);

  const account = wallet.account;
  const isVerified = !!account && (linked.data ?? []).some((w) => sameAddress(w.address, account));

  async function verify() {
    if (!account) return;
    setVerifying(true);
    setVerifyError(null);
    try {
      // 1. The backend stores the exact text and returns it with a single-use nonce.
      const challenge = await walletApi.challenge(account);
      // 2. MetaMask shows that text and signs it. No transaction, no gas, no key leaves the wallet.
      const signature = await metamask.signMessage(challenge.message, account);
      // 3. The backend recovers the signer from the signature. Only its answer counts.
      await walletApi.verify(challenge.nonce, signature);
      linked.reload();
      toast.push({ tone: 'success', message: 'Wallet verified by the backend.' });
    } catch (e) {
      const err = toAppError(e);
      setVerifyError(err.message);
    } finally {
      setVerifying(false);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <Panel title="MetaMask">
        {wallet.status === 'unavailable' ? (
          <EmptyState title="MetaMask was not found">
            Install the MetaMask browser extension, then reload this page. ChainLance only needs your public address and a
            signature.
          </EmptyState>
        ) : (
          <div className="flex flex-col gap-6">
            <dl className="grid gap-5 sm:grid-cols-3">
              <div>
                <dt className="text-sm text-mute">Connection</dt>
                <dd className="mt-1 flex items-center gap-2 font-medium">
                  {account ? (
                    <>
                      <span className="size-2 rounded-full bg-lime-deep ring-1 ring-ink" aria-hidden />
                      Connected
                    </>
                  ) : (
                    <>
                      <Unplug aria-hidden className="size-4 text-mute" />
                      Not connected
                    </>
                  )}
                </dd>
              </div>
              <div className="min-w-0">
                <dt className="text-sm text-mute">Address</dt>
                <dd className="mt-1 break-all font-mono text-sm" title={account ?? undefined}>
                  {account ?? '—'}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-mute">Network</dt>
                <dd className="mt-1 font-medium">{account ? networkName(wallet.chainId) : '—'}</dd>
              </div>
            </dl>

            {account && !wallet.onExpectedChain && (
              <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-ochre p-4">
                <p className="text-sm text-ochre">
                  ChainLance expects {networkName(env.expectedChainId)}. Switch networks before signing transactions later
                  on.
                </p>
                <Button size="sm" variant="outline" onClick={() => void wallet.switchNetwork()}>
                  Switch network
                </Button>
              </div>
            )}

            {wallet.error && (
              <p role="alert" className="rounded-md border border-brick px-4 py-3 text-sm font-medium text-brick">
                {wallet.error.message}
              </p>
            )}

            {!account && (
              <div>
                <Button variant="accent" loading={wallet.status === 'connecting'} onClick={() => void wallet.connect()}>
                  Connect MetaMask
                </Button>
                <p className="mt-3 text-sm text-mute">MetaMask will ask you to approve sharing your public address.</p>
              </div>
            )}
          </div>
        )}
      </Panel>

      {account && (
        <Panel title="Verification">
          <div className="flex flex-col gap-4">
            <div
              className={cn(
                'flex items-start gap-3 rounded-md border p-4',
                isVerified ? 'border-ink bg-lime' : 'border-line bg-paper',
              )}
            >
              {isVerified ? (
                <BadgeCheck aria-hidden className="mt-0.5 size-5 shrink-0" />
              ) : (
                <CircleDashed aria-hidden className="mt-0.5 size-5 shrink-0 text-mute" />
              )}
              <div>
                <p className="font-semibold">{isVerified ? 'Verified by the backend' : 'Not verified yet'}</p>
                <p className="mt-1 text-sm text-ink-soft">
                  {isVerified
                    ? `${shortAddress(account)} is linked to your account.`
                    : 'Being connected is not enough. Sign a short message so the backend can confirm you control this address.'}
                </p>
              </div>
            </div>
            {!isVerified && (
              <>
                <ul className="list-disc space-y-1 pl-5 text-sm text-mute">
                  <li>Signing is free and is not a transaction.</li>
                  <li>ChainLance never asks for your private key or seed phrase.</li>
                  <li>The challenge expires after a few minutes and works once.</li>
                </ul>
                {verifyError && (
                  <p role="alert" className="rounded-md border border-brick px-4 py-3 text-sm font-medium text-brick">
                    {verifyError}
                  </p>
                )}
                <div>
                  <Button variant="primary" loading={verifying} onClick={() => void verify()}>
                    Verify this wallet
                  </Button>
                </div>
              </>
            )}
          </div>
        </Panel>
      )}

      <Panel title="Verified wallets on your account">
        <Async state={linked} rows={2}>
          {(wallets) =>
            wallets.length === 0 ? (
              <EmptyState title="No verified wallets">
                Connect MetaMask and verify a wallet to use escrow later.
              </EmptyState>
            ) : (
              <ul>
                {wallets.map((w) => (
                  <li key={w.id} className="flex flex-wrap items-center justify-between gap-3 border-b border-line py-3 last:border-b-0">
                    <span className="break-all font-mono text-sm">{w.address}</span>
                    <span className="inline-flex items-center gap-1.5 text-sm text-mute">
                      <BadgeCheck aria-hidden className="size-4 text-ink" />
                      Verified {formatDateTime(w.verifiedAt)}
                    </span>
                  </li>
                ))}
              </ul>
            )
          }
        </Async>
        <p className="mt-4 flex items-start gap-2 text-sm text-mute">
          <ShieldAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          Funding escrow and approving milestones need transactions, which the backend does not support yet. Nothing on
          this page sends one.
        </p>
      </Panel>
    </div>
  );
}
