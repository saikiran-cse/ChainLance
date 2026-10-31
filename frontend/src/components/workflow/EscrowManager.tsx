import { useState } from 'react';
import { AlertCircle, CheckCircle2, ExternalLink, Loader2, ShieldCheck, Wallet } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useChainTx } from '../../hooks/useChainTx';
import { useToast } from '../../hooks/useToast';
import { useWallet } from '../../hooks/useWallet';
import { workflowApi, type ChainConfig } from '../../services/workflow.service';
import type { Agreement, AgreementMilestone } from '../../types/marketplace';
import { formatInr, shortAddress, weiToEth } from '../../utils/format';
import { Button } from '../ui/Button';

interface Props {
  agreement: Agreement;
  milestones: AgreementMilestone[];
  chainConfig: ChainConfig | null;
  onRefresh: () => void;
}

export function EscrowManager({ agreement, milestones, chainConfig, onRefresh }: Props) {
  const { user } = useAuth();
  const { account, chainId, connect, onExpectedChain, switchNetwork } = useWallet();
  const { isPending, txHash, createEscrow, fundEscrow } = useChainTx(chainConfig?.chainId);
  const toast = useToast();

  const [linking, setLinking] = useState(false);
  const isClient = user?.id === agreement.clientId;
  const isFreelancer = user?.id === agreement.freelancerId;

  const userWallet = isClient ? agreement.clientWallet : agreement.freelancerWallet;
  const hasLinkedWallet = Boolean(userWallet);

  const handleLinkCurrentWallet = async () => {
    if (!account) {
      await connect();
      return;
    }
    setLinking(true);
    try {
      await workflowApi.linkWallet(agreement.id, account);
      toast.push({ message: 'Wallet linked to this agreement', tone: 'success' });
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Failed to link wallet', tone: 'error' });
    } finally {
      setLinking(false);
    }
  };

  const handleGenerateMilestones = async () => {
    try {
      await workflowApi.generateMilestones(agreement.id);
      toast.push({ message: 'Milestones generated successfully', tone: 'success' });
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Failed to generate milestones', tone: 'error' });
    }
  };

  const handleCreateEscrow = async () => {
    if (account && chainConfig?.adminAddress && account.toLowerCase() === chainConfig.adminAddress.toLowerCase()) {
      toast.push({
        message: 'Your connected MetaMask wallet is the platform Admin address. The admin cannot act as Client on an escrow. Please switch to your Client wallet in MetaMask.',
        tone: 'error',
      });
      return;
    }
    if (account && agreement.freelancerWallet && account.toLowerCase() === agreement.freelancerWallet.toLowerCase()) {
      toast.push({
        message: 'Your connected MetaMask wallet is the Freelancer wallet for this agreement. The client and freelancer must be separate wallets.',
        tone: 'error',
      });
      return;
    }
    if (!chainConfig) {
      toast.push({ message: 'Chain configuration not loaded', tone: 'error' });
      return;
    }
    if (!agreement.freelancerWallet) {
      toast.push({ message: 'Freelancer has not selected their wallet yet', tone: 'error' });
      return;
    }
    if (!agreement.snapshotHash) {
      toast.push({ message: 'Agreement snapshot is not finalized', tone: 'error' });
      return;
    }
    if (milestones.length === 0) {
      toast.push({ message: 'Milestones must be generated first', tone: 'error' });
      return;
    }

    try {
      const allocations = milestones.map((m) => m.amountWei ?? '0');
      const hash = await createEscrow(
        chainConfig.factoryAddress,
        agreement.freelancerWallet,
        agreement.id,
        agreement.snapshotHash,
        allocations
      );
      toast.push({ message: 'Contract created on-chain. Registering with backend...', tone: 'info' });
      await workflowApi.registerEscrow(agreement.id, hash);
      toast.push({ message: 'Escrow registered successfully!', tone: 'success' });
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Escrow creation failed', tone: 'error' });
    }
  };

  const handleFundEscrow = async () => {
    if (!agreement.escrowAddress) {
      toast.push({ message: 'Escrow is not registered', tone: 'error' });
      return;
    }
    if (!agreement.totalWei) {
      toast.push({ message: 'Agreement total wei missing', tone: 'error' });
      return;
    }

    try {
      const hash = await fundEscrow(agreement.escrowAddress, agreement.totalWei);
      toast.push({ message: 'Deposit submitted on-chain. Verifying with backend...', tone: 'info' });
      await workflowApi.verifyFunding(agreement.id, hash);
      toast.push({ message: 'Escrow funded and verified! Milestone 1 is now active.', tone: 'success' });
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Funding failed', tone: 'error' });
    }
  };

  const handleReconcile = async () => {
    try {
      await workflowApi.reconcile(agreement.id);
      toast.push({ message: 'State reconciled with blockchain', tone: 'success' });
      onRefresh();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Reconcile failed', tone: 'error' });
    }
  };

  const etherscanBase = chainId === 11155111 ? 'https://sepolia.etherscan.io' : null;

  return (
    <div className="flex flex-col gap-5 rounded-lg border border-line bg-paper p-5">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-4">
        <div>
          <h3 className="font-display text-lg font-semibold">Blockchain Escrow</h3>
          <p className="text-sm text-mute">
            Non-custodial smart contract escrow on {chainConfig?.chainId === 31337 ? 'Hardhat local node (31337)' : 'Ethereum Sepolia (11155111)'}
          </p>
        </div>
        {agreement.escrowAddress && (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-lime/20 px-3 py-1 text-xs font-semibold text-ink">
            <ShieldCheck className="size-3.5" /> Escrow Deployed
          </span>
        )}
      </div>

      {/* Network & Wallet Status */}
      {account && chainConfig?.adminAddress && account.toLowerCase() === chainConfig.adminAddress.toLowerCase() && (
        <div className="flex items-start gap-2 rounded-md bg-brick/10 p-3 text-sm text-brick">
          <AlertCircle className="mt-0.5 size-4 shrink-0" />
          <span>
            <strong>Connected as Platform Admin ({shortAddress(account)}):</strong> The platform admin arbitrates disputes and cannot create or participate as Client or Freelancer on an escrow contract. Please switch MetaMask to a separate Client wallet.
          </span>
        </div>
      )}
      {!onExpectedChain && account && (
        <div className="flex items-center justify-between rounded-md bg-amber-500/10 p-3 text-sm text-amber-800 dark:text-amber-300">
          <div className="flex items-center gap-2">
            <AlertCircle className="size-4 shrink-0" />
            <span>MetaMask is on chain {chainId}, but chain {chainConfig?.chainId ?? 11155111} is required.</span>
          </div>
          <Button variant="outline" size="sm" onClick={switchNetwork}>
            Switch Network
          </Button>
        </div>
      )}

      {/* Wallet Linking Step */}
      {!hasLinkedWallet && (isClient || isFreelancer) && (
        <div className="flex flex-col gap-2 rounded-md border border-dashed border-amber-500/40 bg-amber-500/5 p-4">
          <p className="text-sm font-medium">Link your wallet for this agreement</p>
          <p className="text-xs text-mute">
            {isClient
              ? 'As the client, this address will own the escrow contract and fund it.'
              : 'As the freelancer, milestone payouts and settlements will be delivered to this address.'}
          </p>
          <div className="mt-2 flex items-center gap-3">
            <Button size="sm" onClick={handleLinkCurrentWallet} disabled={linking}>
              <Wallet className="size-4" />
              {linking ? 'Linking...' : account ? `Link Connected (${shortAddress(account)})` : 'Connect & Link Wallet'}
            </Button>
          </div>
        </div>
      )}

      {/* Escrow Details */}
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <span className="text-xs text-mute">Escrow Address</span>
          <p className="mt-0.5 break-all font-mono text-sm">
            {agreement.escrowAddress ? (
              etherscanBase ? (
                <a
                  href={`${etherscanBase}/address/${agreement.escrowAddress}`}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 text-ink underline hover:text-accent"
                >
                  {agreement.escrowAddress}
                  <ExternalLink className="size-3" />
                </a>
              ) : (
                agreement.escrowAddress
              )
            ) : (
              'Not deployed yet'
            )}
          </p>
        </div>
        <div>
          <span className="text-xs text-mute">Escrow Status</span>
          <p className="mt-0.5 text-sm font-medium">
            {agreement.status === 'FUNDED' ? (
              <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
                <CheckCircle2 className="size-4" /> Funded & Active
              </span>
            ) : agreement.status === 'COMPLETED' ? (
              <span className="inline-flex items-center gap-1 text-accent">
                <CheckCircle2 className="size-4" /> Completed & Fully Released
              </span>
            ) : agreement.escrowAddress ? (
              <span className="text-amber-600">Created, Awaiting Funding</span>
            ) : (
              <span className="text-mute">Awaiting Escrow Creation</span>
            )}
          </p>
        </div>
        <div>
          <span className="text-xs text-mute">Total Budget</span>
          <p className="mt-0.5 text-sm font-semibold">
            {agreement.priceInr ? formatInr(agreement.priceInr) : '—'}
            {agreement.totalWei && <span className="ml-1.5 font-normal text-mute">({weiToEth(agreement.totalWei)} ETH)</span>}
          </p>
        </div>
        <div>
          <span className="text-xs text-mute">Required Confirmations</span>
          <p className="mt-0.5 text-sm">{chainConfig?.requiredConfirmations ?? 1} on-chain block</p>
        </div>
      </div>

      {/* Actions */}
      <div className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
        {/* Step A: Client generates milestones */}
        {isClient && milestones.length === 0 && agreement.status === 'FINALIZED' && (
          <Button onClick={handleGenerateMilestones} size="sm">
            Generate Milestone Plan
          </Button>
        )}

        {/* Step B: Client creates escrow contract */}
        {isClient && !agreement.escrowAddress && milestones.length > 0 && (
          <Button
            onClick={handleCreateEscrow}
            disabled={isPending || !agreement.clientWallet || !agreement.freelancerWallet}
            size="sm"
          >
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {isPending ? 'Deploying Escrow...' : 'Deploy Escrow Contract (MetaMask)'}
          </Button>
        )}

        {/* Step C: Client funds escrow contract */}
        {isClient && agreement.escrowAddress && agreement.status !== 'FUNDED' && agreement.status !== 'COMPLETED' && (
          <Button onClick={handleFundEscrow} disabled={isPending} size="sm">
            {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {isPending ? 'Funding Escrow...' : `Deposit ${agreement.totalWei ? weiToEth(agreement.totalWei) : ''} ETH & Fund`}
          </Button>
        )}

        {/* Reconcile state button */}
        {agreement.escrowAddress && (
          <Button variant="outline" size="sm" onClick={handleReconcile}>
            Sync / Reconcile On-chain State
          </Button>
        )}

        {txHash && (
          <span className="text-xs text-mute">
            Last Tx:{' '}
            {etherscanBase ? (
              <a href={`${etherscanBase}/tx/${txHash}`} target="_blank" rel="noreferrer" className="underline">
                {shortAddress(txHash)}
              </a>
            ) : (
              shortAddress(txHash)
            )}
          </span>
        )}
      </div>
    </div>
  );
}
