// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {Ownable2Step} from "@openzeppelin/contracts/access/Ownable2Step.sol";
import {ChainLanceEscrow} from "./ChainLanceEscrow.sol";

/**
 * @title ChainLanceFactory
 * @notice Deploys one ChainLanceEscrow per accepted project. The factory owner IS the platform admin;
 *         ownership moves in two steps (transferOwnership + acceptOwnership) to avoid typos locking the role.
 *         The admin is copied into each escrow at creation, so rotating the owner affects only NEW escrows.
 */
contract ChainLanceFactory is Ownable2Step {
    mapping(bytes32 => address) public escrowOf;
    address[] private _escrows;

    error EscrowAlreadyExists(bytes32 projectId);
    error RenounceDisabled();

    event EscrowCreated(
        bytes32 indexed projectId,
        address indexed escrow,
        address indexed client,
        address freelancer,
        address admin,
        uint256 totalAmount
    );

    constructor(address initialAdmin) Ownable(initialAdmin) {}

    /// @notice msg.sender becomes the client. projectId must be unique (backend uses a hash of the agreement snapshot).
    function createEscrow(address freelancer, bytes32 projectId, uint256[] calldata allocations)
        external
        returns (address escrow)
    {
        if (escrowOf[projectId] != address(0)) revert EscrowAlreadyExists(projectId);
        ChainLanceEscrow e = new ChainLanceEscrow(msg.sender, freelancer, owner(), projectId, allocations);
        escrow = address(e);
        escrowOf[projectId] = escrow;
        _escrows.push(escrow);
        emit EscrowCreated(projectId, escrow, msg.sender, freelancer, owner(), e.totalAmount());
    }

    function escrowCount() external view returns (uint256) {
        return _escrows.length;
    }

    function escrowAt(uint256 i) external view returns (address) {
        return _escrows[i];
    }

    function renounceOwnership() public pure override {
        revert RenounceDisabled();
    }
}
