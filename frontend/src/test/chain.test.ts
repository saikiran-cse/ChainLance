import { describe, expect, it } from 'vitest';
import { ethers } from 'ethers';
import { computeOnchainProjectId, hashReason } from '../utils/chain';

describe('chain utils', () => {
  it('computes on-chain project ID matching ethers solidityPackedKeccak256', () => {
    const agreementId = 42;
    const snapshotHash = '0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef';
    const expected = ethers.solidityPackedKeccak256(['uint256', 'bytes32'], [agreementId, snapshotHash]);
    const actual = computeOnchainProjectId(agreementId, snapshotHash);
    expect(actual).toBe(expected);
  });

  it('computes on-chain project ID even if snapshotHash lacks 0x prefix', () => {
    const agreementId = 42;
    const rawHash = '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef';
    const expected = ethers.solidityPackedKeccak256(['uint256', 'bytes32'], [agreementId, `0x${rawHash}`]);
    const actual = computeOnchainProjectId(agreementId, rawHash);
    expect(actual).toBe(expected);
  });

  it('hashes dispute/settlement reasons using utf-8 keccak256', () => {
    const reason = 'Work was not delivered on time';
    const expected = ethers.keccak256(ethers.toUtf8Bytes(reason.trim()));
    const actual = hashReason(reason);
    expect(actual).toBe(expected);
  });
});
