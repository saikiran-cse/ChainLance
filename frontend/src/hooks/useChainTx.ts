import { useCallback, useState } from 'react';
import { env } from '../config/env';
import {
  computeOnchainProjectId,
  getEscrowContract,
  getFactoryContract,
  getSigner,
  hashReason,
} from '../utils/chain';
import { AppError, toAppError } from '../utils/errors';

export interface ChainTxState {
  isPending: boolean;
  txHash: string | null;
  error: AppError | null;
}

export function useChainTx(expectedChainId = env.expectedChainId) {
  const [state, setState] = useState<ChainTxState>({
    isPending: false,
    txHash: null,
    error: null,
  });

  const clear = useCallback(() => {
    setState({ isPending: false, txHash: null, error: null });
  }, []);

  const createEscrow = useCallback(
    async (
      factoryAddress: string,
      freelancerWallet: string,
      agreementId: number,
      snapshotHash: string,
      allocationsWei: (string | bigint)[]
    ) => {
      setState({ isPending: true, txHash: null, error: null });
      try {
        const { signer } = await getSigner(expectedChainId);
        const factory = getFactoryContract(factoryAddress, signer);
        const projectIdHex = computeOnchainProjectId(agreementId, snapshotHash);
        const allocations = allocationsWei.map((a) => BigInt(a.toString()));

        const tx = await factory.createEscrow(freelancerWallet, projectIdHex, allocations);
        setState((s) => ({ ...s, txHash: tx.hash }));
        const receipt = await tx.wait(1);
        setState({ isPending: false, txHash: receipt.hash, error: null });
        return receipt.hash as string;
      } catch (err) {
        const appErr = toAppError(err);
        setState({ isPending: false, txHash: null, error: appErr });
        throw appErr;
      }
    },
    [expectedChainId]
  );

  const fundEscrow = useCallback(
    async (escrowAddress: string, totalWei: string | bigint) => {
      setState({ isPending: true, txHash: null, error: null });
      try {
        const { signer } = await getSigner(expectedChainId);
        const escrow = getEscrowContract(escrowAddress, signer);
        const value = BigInt(totalWei.toString());

        const tx = await escrow.fund({ value });
        setState((s) => ({ ...s, txHash: tx.hash }));
        const receipt = await tx.wait(1);
        setState({ isPending: false, txHash: receipt.hash, error: null });
        return receipt.hash as string;
      } catch (err) {
        const appErr = toAppError(err);
        setState({ isPending: false, txHash: null, error: appErr });
        throw appErr;
      }
    },
    [expectedChainId]
  );

  const approveMilestone = useCallback(
    async (escrowAddress: string, index: number) => {
      setState({ isPending: true, txHash: null, error: null });
      try {
        const { signer } = await getSigner(expectedChainId);
        const escrow = getEscrowContract(escrowAddress, signer);

        const tx = await escrow.approveMilestone(index);
        setState((s) => ({ ...s, txHash: tx.hash }));
        const receipt = await tx.wait(1);
        setState({ isPending: false, txHash: receipt.hash, error: null });
        return receipt.hash as string;
      } catch (err) {
        const appErr = toAppError(err);
        setState({ isPending: false, txHash: null, error: appErr });
        throw appErr;
      }
    },
    [expectedChainId]
  );

  const proposeSettlement = useCallback(
    async (
      escrowAddress: string,
      index: number,
      freelancerWei: string | bigint,
      clientWei: string | bigint
    ) => {
      setState({ isPending: true, txHash: null, error: null });
      try {
        const { signer } = await getSigner(expectedChainId);
        const escrow = getEscrowContract(escrowAddress, signer);

        const tx = await escrow.proposeSettlement(
          index,
          BigInt(freelancerWei.toString()),
          BigInt(clientWei.toString())
        );
        setState((s) => ({ ...s, txHash: tx.hash }));
        const receipt = await tx.wait(1);
        setState({ isPending: false, txHash: receipt.hash, error: null });
        return receipt.hash as string;
      } catch (err) {
        const appErr = toAppError(err);
        setState({ isPending: false, txHash: null, error: appErr });
        throw appErr;
      }
    },
    [expectedChainId]
  );

  const acceptSettlement = useCallback(
    async (
      escrowAddress: string,
      index: number,
      freelancerWei: string | bigint,
      clientWei: string | bigint
    ) => {
      setState({ isPending: true, txHash: null, error: null });
      try {
        const { signer } = await getSigner(expectedChainId);
        const escrow = getEscrowContract(escrowAddress, signer);

        const tx = await escrow.acceptSettlement(
          index,
          BigInt(freelancerWei.toString()),
          BigInt(clientWei.toString())
        );
        setState((s) => ({ ...s, txHash: tx.hash }));
        const receipt = await tx.wait(1);
        setState({ isPending: false, txHash: receipt.hash, error: null });
        return receipt.hash as string;
      } catch (err) {
        const appErr = toAppError(err);
        setState({ isPending: false, txHash: null, error: appErr });
        throw appErr;
      }
    },
    [expectedChainId]
  );

  const withdrawSettlement = useCallback(
    async (escrowAddress: string, index: number) => {
      setState({ isPending: true, txHash: null, error: null });
      try {
        const { signer } = await getSigner(expectedChainId);
        const escrow = getEscrowContract(escrowAddress, signer);

        const tx = await escrow.withdrawSettlement(index);
        setState((s) => ({ ...s, txHash: tx.hash }));
        const receipt = await tx.wait(1);
        setState({ isPending: false, txHash: receipt.hash, error: null });
        return receipt.hash as string;
      } catch (err) {
        const appErr = toAppError(err);
        setState({ isPending: false, txHash: null, error: appErr });
        throw appErr;
      }
    },
    [expectedChainId]
  );

  const openDispute = useCallback(
    async (escrowAddress: string, index: number, reason: string) => {
      setState({ isPending: true, txHash: null, error: null });
      try {
        const { signer } = await getSigner(expectedChainId);
        const escrow = getEscrowContract(escrowAddress, signer);
        const reasonHash = hashReason(reason);

        const tx = await escrow.openDispute(index, reasonHash);
        setState((s) => ({ ...s, txHash: tx.hash }));
        const receipt = await tx.wait(1);
        setState({ isPending: false, txHash: receipt.hash, error: null });
        return { txHash: receipt.hash as string, reasonHash };
      } catch (err) {
        const appErr = toAppError(err);
        setState({ isPending: false, txHash: null, error: appErr });
        throw appErr;
      }
    },
    [expectedChainId]
  );

  const resolveDispute = useCallback(
    async (
      escrowAddress: string,
      index: number,
      freelancerWei: string | bigint,
      clientWei: string | bigint,
      resolutionExplanation: string
    ) => {
      setState({ isPending: true, txHash: null, error: null });
      try {
        const { signer } = await getSigner(expectedChainId);
        const escrow = getEscrowContract(escrowAddress, signer);
        const resolutionHash = hashReason(resolutionExplanation);

        const tx = await escrow.resolveDispute(
          index,
          BigInt(freelancerWei.toString()),
          BigInt(clientWei.toString()),
          resolutionHash
        );
        setState((s) => ({ ...s, txHash: tx.hash }));
        const receipt = await tx.wait(1);
        setState({ isPending: false, txHash: receipt.hash, error: null });
        return { txHash: receipt.hash as string, resolutionHash };
      } catch (err) {
        const appErr = toAppError(err);
        setState({ isPending: false, txHash: null, error: appErr });
        throw appErr;
      }
    },
    [expectedChainId]
  );

  return {
    ...state,
    clear,
    createEscrow,
    fundEscrow,
    approveMilestone,
    proposeSettlement,
    acceptSettlement,
    withdrawSettlement,
    openDispute,
    resolveDispute,
  };
}
