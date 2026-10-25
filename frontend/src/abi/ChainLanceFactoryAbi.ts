export const ChainLanceFactoryAbi = [
  {
    type: 'constructor',
    inputs: [{ name: 'admin_', type: 'address' }],
    stateMutability: 'nonpayable',
  },
  {
    type: 'function',
    name: 'admin',
    inputs: [],
    outputs: [{ name: '', type: 'address' }],
    stateMutability: 'view',
  },
  {
    type: 'function',
    name: 'createEscrow',
    inputs: [
      { name: 'freelancer', type: 'address' },
      { name: 'projectId', type: 'bytes32' },
      { name: 'allocations', type: 'uint256[]' },
    ],
    outputs: [{ name: 'escrow', type: 'address' }],
    stateMutability: 'nonpayable',
  },
  {
    type: 'function',
    name: 'escrowOf',
    inputs: [
      { name: 'client', type: 'address' },
      { name: 'projectId', type: 'bytes32' },
    ],
    outputs: [{ name: '', type: 'address' }],
    stateMutability: 'view',
  },
  {
    type: 'event',
    name: 'EscrowCreated',
    inputs: [
      { name: 'projectId', type: 'bytes32', indexed: true },
      { name: 'escrow', type: 'address', indexed: true },
      { name: 'client', type: 'address', indexed: true },
      { name: 'freelancer', type: 'address', indexed: false },
      { name: 'admin', type: 'address', indexed: false },
      { name: 'totalAmount', type: 'uint256', indexed: false },
    ],
    anonymous: false,
  },
] as const;
