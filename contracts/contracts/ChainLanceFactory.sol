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
 *
 *         Duplicate protection is scoped to the CALLER (client). Project ids are visible in the public mempool,
 *         so a global registry would let anyone front-run a client's createEscrow with the same id and block that
 *         agreement forever. The backend binds an escrow to an agreement by checking the escrow's client and
 *         projectId against the agreement, never by trusting the id alone.
 */
contract ChainLanceFactory is Ownable2Step {
    /// @notice escrowOf[client][projectId]: the escrow that `client` created for `projectId` (zero if none).
    mapping(address => mapping(bytes32 => address)) public escrowOf;
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

    /// @notice msg.sender becomes the client. A client can use a projectId only once (backend derives it from the agreement snapshot).
    function createEscrow(address freelancer, bytes32 projectId, uint256[] calldata allocations)
        external
        returns (address escrow)
    {
        if (escrowOf[msg.sender][projectId] != address(0)) revert EscrowAlreadyExists(projectId);
        ChainLanceEscrow e = new ChainLanceEscrow(msg.sender, freelancer, owner(), projectId, allocations);
        escrow = address(e);
        escrowOf[msg.sender][projectId] = escrow;
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
