// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

interface IChainLanceFactoryLike {
    function createEscrow(address freelancer, bytes32 projectId, uint256[] calldata allocations)
        external
        returns (address);
}

interface IChainLanceEscrowLike {
    function fund() external payable;

    function openDispute(uint256 index, bytes32 reasonHash) external;
}

/**
 * @dev TEST ONLY. A smart-contract CLIENT that can create, fund and dispute an escrow and whose ETH
 *      reception can be switched off, to prove that failed refunds revert atomically.
 *      Its functions are open to anyone because it only ever exists inside local tests.
 */
contract RejectingClient {
    bool public rejectPayments;

    function setRejectPayments(bool value) external {
        rejectPayments = value;
    }

    function createEscrow(address factory, address freelancer, bytes32 projectId, uint256[] calldata allocations)
        external
        returns (address)
    {
        return IChainLanceFactoryLike(factory).createEscrow(freelancer, projectId, allocations);
    }

    function fund(address escrow) external payable {
        IChainLanceEscrowLike(escrow).fund{value: msg.value}();
    }

    function openDispute(address escrow, uint256 index, bytes32 reasonHash) external {
        IChainLanceEscrowLike(escrow).openDispute(index, reasonHash);
    }

    receive() external payable {
        if (rejectPayments) revert("payments rejected");
    }
}
