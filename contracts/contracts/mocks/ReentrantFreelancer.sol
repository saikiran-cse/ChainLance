// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

interface IEscrowLike {
    function proposeSettlement(uint256 index, uint256 f, uint256 c) external;
}

/// @dev TEST ONLY. A freelancer contract that tries to re-enter the escrow when it receives ETH.
contract ReentrantFreelancer {
    IEscrowLike public escrow;
    bool public reentryAttempted;
    bool public reentryBlocked;
    bytes4 public lastSelector;

    function setEscrow(address e) external {
        escrow = IEscrowLike(e);
    }

    receive() external payable {
        if (address(escrow) == address(0)) return;
        reentryAttempted = true;
        try escrow.proposeSettlement(1, 1, 1) {} catch (bytes memory reason) {
            reentryBlocked = true;
            if (reason.length >= 4) lastSelector = bytes4(reason);
        }
    }
}
