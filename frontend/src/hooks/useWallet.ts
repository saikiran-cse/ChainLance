import { useCallback, useEffect, useState } from 'react';
import { env } from '../config/env';
import { metamask } from '../services/wallet.service';
import { AppError, toAppError } from '../utils/errors';

export type WalletConnection = 'unavailable' | 'disconnected' | 'connecting' | 'connected';

/**
 * Browser-side MetaMask state: which account is connected and on which network.
 * "Connected" is NOT "verified": verification is decided only by the backend (see WalletPanel).
 */
export function useWallet() {
  const available = metamask.isAvailable();
  const [account, setAccount] = useState<string | null>(null);
  const [chainId, setChainId] = useState<number | null>(null);
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState<AppError | null>(null);

  useEffect(() => {
    if (!available) return;
    let alive = true;
    void (async () => {
      // eth_accounts is read-only and never opens a prompt.
      const [existing, chain] = await Promise.all([metamask.existingAccount(), metamask.chainId()]);
      if (!alive) return;
      setAccount(existing);
      setChainId(chain);
    })();
    const unsubscribe = metamask.subscribe({ onAccounts: setAccount, onChain: setChainId });
    return () => {
      alive = false;
      unsubscribe();
    };
  }, [available]);

  const connect = useCallback(async () => {
    setError(null);
    setConnecting(true);
    try {
      const next = await metamask.connect();
      setAccount(next);
      setChainId(await metamask.chainId());
    } catch (e) {
      setError(toAppError(e));
    } finally {
      setConnecting(false);
    }
  }, []);

  const switchNetwork = useCallback(async () => {
    setError(null);
    try {
      await metamask.switchToExpectedChain();
      setChainId(await metamask.chainId());
    } catch (e) {
      setError(toAppError(e));
    }
  }, []);

  const clearError = useCallback(() => setError(null), []);

  const status: WalletConnection = !available
    ? 'unavailable'
    : connecting
      ? 'connecting'
      : account
        ? 'connected'
        : 'disconnected';

  return {
    status,
    account,
    chainId,
    error,
    connect,
    switchNetwork,
    clearError,
    onExpectedChain: chainId === env.expectedChainId,
  };
}
