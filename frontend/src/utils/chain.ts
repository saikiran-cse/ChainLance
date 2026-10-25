import { BrowserProvider, Contract, ethers, type JsonRpcSigner } from 'ethers';
import { ChainLanceEscrowAbi, ChainLanceFactoryAbi } from '../abi';
import { env } from '../config/env';
import { getProvider } from '../services/wallet.service';
import { AppError } from './errors';

export function computeOnchainProjectId(agreementId: number, snapshotHash: string): string {
  const normalizedHash = snapshotHash.startsWith('0x') ? snapshotHash : `0x${snapshotHash}`;
  return ethers.solidityPackedKeccak256(['uint256', 'bytes32'], [agreementId, normalizedHash]);
}

export function hashReason(reason: string): string {
  return ethers.keccak256(ethers.toUtf8Bytes(reason.trim()));
}

export async function getSigner(expectedChainId = env.expectedChainId): Promise<{ signer: JsonRpcSigner; provider: BrowserProvider }> {
  const eip1193 = getProvider();
  if (!eip1193) {
    throw new AppError('MetaMask is not installed or available in this browser.', { kind: 'unknown', code: 'METAMASK_UNAVAILABLE' });
  }

  const provider = new BrowserProvider(eip1193 as ethers.Eip1193Provider);
  const network = await provider.getNetwork();
  const currentChainId = Number(network.chainId);

  if (currentChainId !== expectedChainId) {
    try {
      await eip1193.request({
        method: 'wallet_switchEthereumChain',
        params: [{ chainId: `0x${expectedChainId.toString(16)}` }],
      });
    } catch {
      throw new AppError(
        `Please switch MetaMask to network chain ID ${expectedChainId} (currently on ${currentChainId}).`,
        { kind: 'unknown', code: 'WRONG_NETWORK' }
      );
    }
  }

  const signer = await provider.getSigner();
  return { signer, provider };
}

export function getFactoryContract(factoryAddress: string, signer: JsonRpcSigner) {
  return new Contract(factoryAddress, ChainLanceFactoryAbi, signer);
}

export function getEscrowContract(escrowAddress: string, signer: JsonRpcSigner) {
  return new Contract(escrowAddress, ChainLanceEscrowAbi, signer);
}
