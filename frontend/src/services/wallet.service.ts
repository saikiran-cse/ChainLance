import { env } from '../config/env';
import type { Wallet, WalletChallenge } from '../types/api';
import { AppError, guard } from '../utils/errors';
import { api } from './api';

/* ------------------------------------------------------------------------------------------------------------------
 * Backend side. LIVE: WalletController exposes exactly these three endpoints.
 * "Verified" means one thing: the backend recovered the signer from the signature and stored the wallet.
 * ---------------------------------------------------------------------------------------------------------------- */
export const walletApi = {
  /** Step 1: the server stores the exact text to sign and returns it with a single-use nonce. */
  async challenge(address: string): Promise<WalletChallenge> {
    const { data } = await guard(api.post<WalletChallenge>('/api/wallet/challenge', { address }));
    return data;
  },

  /** Step 2: the backend verifies the signature against its stored message. Only success makes a wallet "verified". */
  async verify(nonce: string, signature: string): Promise<Wallet> {
    const { data } = await guard(api.post<Wallet>('/api/wallet/verify', { nonce, signature }));
    return data;
  },

  /** Wallets the backend has verified for the signed-in user. */
  async list(): Promise<Wallet[]> {
    const { data } = await guard(api.get<Wallet[]>('/api/wallet'));
    return data;
  },
};

/* ------------------------------------------------------------------------------------------------------------------
 * Browser side: MetaMask through the standard EIP-1193 `window.ethereum` provider.
 * The app only ever asks for the PUBLIC address and a signature over text the backend supplied.
 * It never asks for, reads or transmits a private key or seed phrase, and sends no transactions yet.
 * ---------------------------------------------------------------------------------------------------------------- */
export interface Eip1193Provider {
  isMetaMask?: boolean;
  providers?: Eip1193Provider[];
  request(args: { method: string; params?: unknown[] | object }): Promise<unknown>;
  on?(event: string, handler: (...args: unknown[]) => void): void;
  removeListener?(event: string, handler: (...args: unknown[]) => void): void;
}

declare global {
  interface Window {
    ethereum?: Eip1193Provider;
  }
}

export const NETWORK_NAMES: Record<number, string> = {
  1: 'Ethereum Mainnet',
  11155111: 'Ethereum Sepolia',
  17000: 'Ethereum Holesky',
  31337: 'Hardhat local',
  137: 'Polygon',
  8453: 'Base',
  10: 'OP Mainnet',
  42161: 'Arbitrum One',
};

export function networkName(chainId: number | null): string {
  if (chainId == null) return 'Unknown network';
  return NETWORK_NAMES[chainId] ?? `Chain ${chainId}`;
}

export function getProvider(): Eip1193Provider | null {
  if (typeof window === 'undefined' || !window.ethereum) return null;
  const injected = window.ethereum;
  // With several wallets installed, prefer MetaMask explicitly.
  const metaMask = injected.providers?.find((p) => p.isMetaMask);
  return metaMask ?? injected;
}

interface ProviderRpcError {
  code: number;
  message?: string;
}

function isProviderError(error: unknown): error is ProviderRpcError {
  return typeof error === 'object' && error !== null && typeof (error as ProviderRpcError).code === 'number';
}

function asProviderError(error: unknown, fallback: string): AppError {
  if (isProviderError(error)) {
    if (error.code === 4001) {
      return new AppError('You rejected the request in MetaMask. Nothing was changed.', {
        kind: 'forbidden',
        code: 'WALLET_REJECTED',
      });
    }
    if (error.code === -32002) {
      return new AppError('MetaMask already has a request waiting. Open the MetaMask window to continue.', {
        kind: 'conflict',
        code: 'WALLET_REQUEST_PENDING',
      });
    }
    return new AppError(error.message || fallback, { kind: 'unknown', code: `WALLET_${error.code}` });
  }
  return new AppError(error instanceof Error ? error.message : fallback, { kind: 'unknown' });
}

function requireProvider(): Eip1193Provider {
  const provider = getProvider();
  if (!provider) {
    throw new AppError('MetaMask was not found in this browser. Install the MetaMask extension and reload.', {
      kind: 'unknown',
      code: 'WALLET_NOT_INSTALLED',
    });
  }
  return provider;
}

function parseChainId(value: unknown): number | null {
  if (typeof value === 'string') {
    const n = value.startsWith('0x') ? parseInt(value, 16) : Number(value);
    return Number.isFinite(n) ? n : null;
  }
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function firstAccount(value: unknown): string | null {
  return Array.isArray(value) && typeof value[0] === 'string' ? value[0].toLowerCase() : null;
}

function toHex(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let out = '0x';
  for (const b of bytes) out += b.toString(16).padStart(2, '0');
  return out;
}

export const metamask = {
  isAvailable(): boolean {
    return getProvider() !== null;
  },

  /** Read-only: accounts the site is already permitted to see. Does not prompt, and does NOT mean "verified". */
  async existingAccount(): Promise<string | null> {
    const provider = getProvider();
    if (!provider) return null;
    try {
      return firstAccount(await provider.request({ method: 'eth_accounts' }));
    } catch {
      return null;
    }
  },

  /** Opens the MetaMask connect prompt. Rejections are mapped to a readable AppError. */
  async connect(): Promise<string> {
    const provider = requireProvider();
    try {
      const account = firstAccount(await provider.request({ method: 'eth_requestAccounts' }));
      if (!account) throw new AppError('MetaMask did not share an account.', { kind: 'unknown' });
      return account;
    } catch (e) {
      throw asProviderError(e, 'Could not connect to MetaMask.');
    }
  },

  async chainId(): Promise<number | null> {
    const provider = getProvider();
    if (!provider) return null;
    try {
      return parseChainId(await provider.request({ method: 'eth_chainId' }));
    } catch {
      return null;
    }
  },

  /** Ask MetaMask to switch to the chain this app expects (EIP-3326). */
  async switchToExpectedChain(): Promise<void> {
    const provider = requireProvider();
    try {
      await provider.request({
        method: 'wallet_switchEthereumChain',
        params: [{ chainId: `0x${env.expectedChainId.toString(16)}` }],
      });
    } catch (e) {
      if (isProviderError(e) && e.code === 4902) {
        throw new AppError(
          `MetaMask does not know ${networkName(env.expectedChainId)} yet. Add that network in MetaMask, then try again.`,
          { kind: 'unknown', code: 'WALLET_CHAIN_UNKNOWN' },
        );
      }
      throw asProviderError(e, 'Could not switch network.');
    }
  },

  /**
   * personal_sign (EIP-191) over the backend's challenge text. The text is hex-encoded the same way ethers.js does it,
   * so MetaMask shows the readable message and the signature matches what the backend recovers.
   */
  async signMessage(message: string, account: string): Promise<string> {
    const provider = requireProvider();
    try {
      const signature = await provider.request({ method: 'personal_sign', params: [toHex(message), account] });
      if (typeof signature !== 'string') throw new AppError('MetaMask returned no signature.', { kind: 'unknown' });
      return signature;
    } catch (e) {
      throw asProviderError(e, 'Could not sign the message.');
    }
  },

  /** Subscribe to account and network changes. Returns an unsubscribe function. */
  subscribe(handlers: { onAccounts: (account: string | null) => void; onChain: (chainId: number | null) => void }): () => void {
    const provider = getProvider();
    if (!provider?.on) return () => undefined;
    const onAccounts = (accounts: unknown) => handlers.onAccounts(firstAccount(accounts));
    const onChain = (chainId: unknown) => handlers.onChain(parseChainId(chainId));
    provider.on('accountsChanged', onAccounts);
    provider.on('chainChanged', onChain);
    return () => {
      provider.removeListener?.('accountsChanged', onAccounts);
      provider.removeListener?.('chainChanged', onChain);
    };
  },
};
