// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {Address} from "@openzeppelin/contracts/utils/Address.sol";

/**
 * @title ChainLanceEscrow
 * @notice One escrow per accepted project. Holds the ENTIRE agreed budget in native ETH
 *         and releases it per milestone.
 *
 * Money paths (each milestone is resolved exactly once, atomically):
 *   1. approveMilestone      client approves -> full allocation to freelancer
 *   2. acceptSettlement      one party proposes a split, the OTHER accepts the exact same split
 *   3. resolveDispute        admin splits a DISPUTED milestone
 *
 * Trust assumptions (see docs/ARCHITECTURE.md):
 *   - The admin is trusted only to split a disputed milestone's own allocation.
 *     The admin cannot withdraw other funds, touch resolved milestones, or touch undisputed ones.
 *   - A client who never approves and never disputes locks only that milestone until a dispute is opened.
 *   - Deadlines are NOT enforced on-chain; they live in the backend (documented design decision).
 */
contract ChainLanceEscrow is ReentrancyGuard {
    using Address for address payable;

    enum MilestoneStatus {
        Open, // 0: funded, unresolved
        Released, // 1: approved, full amount paid to freelancer
        Settled, // 2: agreed split executed (may be a full refund)
        Disputed, // 3: frozen, only admin can resolve
        DisputeResolved // 4: admin split executed
    }

    struct Milestone {
        uint256 allocation; // wei, fixed at deployment
        MilestoneStatus status;
        uint256 freelancerPaid; // wei actually paid out
        uint256 clientRefunded; // wei actually refunded
        address disputedBy; // zero unless disputed
    }

    struct SettlementProposal {
        bool active;
        address proposer;
        uint256 freelancerAmount;
        uint256 clientAmount;
    }

    uint256 public constant MAX_MILESTONES = 20;

    address public immutable client;
    address public immutable freelancer;
    address public immutable admin;
    bytes32 public immutable projectId;
    uint256 public immutable totalAmount;

    bool public funded;
    uint256 public totalPaidToFreelancer;
    uint256 public totalRefundedToClient;
    uint256 public unresolvedMilestones;

    Milestone[] private _milestones;
    mapping(uint256 => SettlementProposal) private _proposals;

    // ---------------------------------------------------------------- errors
    error ZeroAddress();
    error DuplicateParty();
    error InvalidProjectId();
    error InvalidMilestoneCount();
    error ZeroAllocation();
    error NotClient();
    error NotAdmin();
    error NotParty();
    error NotFunded();
    error AlreadyFunded();
    error IncorrectFunding(uint256 sent, uint256 expected);
    error InvalidMilestone();
    error InvalidStatus(MilestoneStatus current);
    error AmountsMustSumToAllocation(uint256 sum, uint256 allocation);
    error NoActiveProposal();
    error CannotAcceptOwnProposal();
    error ProposalMismatch();
    error NotProposer();
    error DirectPaymentRejected();

    // ---------------------------------------------------------------- events
    event Funded(address indexed client, uint256 amount);
    event MilestoneReleased(uint256 indexed index, address indexed freelancer, uint256 amount);
    event SettlementProposed(uint256 indexed index, address indexed proposer, uint256 freelancerAmount, uint256 clientAmount);
    event SettlementWithdrawn(uint256 indexed index, address indexed proposer);
    event MilestoneSettled(uint256 indexed index, uint256 freelancerAmount, uint256 clientAmount);
    event DisputeOpened(uint256 indexed index, address indexed openedBy, bytes32 reasonHash);
    event DisputeResolved(uint256 indexed index, address indexed admin, uint256 freelancerAmount, uint256 clientAmount, bytes32 resolutionHash);
    event EscrowCompleted();

    // ------------------------------------------------------------- modifiers
    modifier onlyClient() {
        if (msg.sender != client) revert NotClient();
        _;
    }
    modifier onlyAdmin() {
        if (msg.sender != admin) revert NotAdmin();
        _;
    }
    modifier onlyParty() {
        if (msg.sender != client && msg.sender != freelancer) revert NotParty();
        _;
    }
    modifier whenFunded() {
        if (!funded) revert NotFunded();
        _;
    }

    constructor(
        address client_,
        address freelancer_,
        address admin_,
        bytes32 projectId_,
        uint256[] memory allocations
    ) {
        if (client_ == address(0) || freelancer_ == address(0) || admin_ == address(0)) revert ZeroAddress();
        if (client_ == freelancer_ || admin_ == client_ || admin_ == freelancer_) revert DuplicateParty();
        if (projectId_ == bytes32(0)) revert InvalidProjectId();
        if (allocations.length == 0 || allocations.length > MAX_MILESTONES) revert InvalidMilestoneCount();

        uint256 total;
        for (uint256 i = 0; i < allocations.length; i++) {
            if (allocations[i] == 0) revert ZeroAllocation();
            total += allocations[i];
            _milestones.push(Milestone(allocations[i], MilestoneStatus.Open, 0, 0, address(0)));
        }

        client = client_;
        freelancer = freelancer_;
        admin = admin_;
        projectId = projectId_;
        totalAmount = total;
        unresolvedMilestones = allocations.length;
    }

    // ---------------------------------------------------------------- funding
    /// @notice Client deposits the entire budget in one transaction. Exact amount only, once only.
    function fund() external payable onlyClient nonReentrant {
        if (funded) revert AlreadyFunded();
        if (msg.value != totalAmount) revert IncorrectFunding(msg.value, totalAmount);
        funded = true;
        emit Funded(msg.sender, msg.value);
    }

    // ------------------------------------------------------ 1. client approval
    function approveMilestone(uint256 index) external nonReentrant onlyClient whenFunded {
        Milestone storage m = _open(index);
        emit MilestoneReleased(index, freelancer, m.allocation);
        _close(index, m, MilestoneStatus.Released, m.allocation, 0);
    }

    // ------------------------------------------------- 2. agreed settlement
    /// @notice Either party proposes an exact split of an open milestone. A new proposal replaces the old one.
    function proposeSettlement(uint256 index, uint256 freelancerAmount, uint256 clientAmount)
        external
        nonReentrant
        onlyParty
        whenFunded
    {
        Milestone storage m = _open(index);
        _checkSum(m, freelancerAmount, clientAmount);
        _proposals[index] = SettlementProposal(true, msg.sender, freelancerAmount, clientAmount);
        emit SettlementProposed(index, msg.sender, freelancerAmount, clientAmount);
    }

    function withdrawSettlement(uint256 index) external nonReentrant {
        SettlementProposal storage p = _proposals[index];
        if (!p.active) revert NoActiveProposal();
        if (p.proposer != msg.sender) revert NotProposer();
        delete _proposals[index];
        emit SettlementWithdrawn(index, msg.sender);
    }

    /// @notice The OTHER party accepts. The amounts are re-supplied so a proposal replaced in the same block cannot be accepted by mistake.
    function acceptSettlement(uint256 index, uint256 freelancerAmount, uint256 clientAmount)
        external
        nonReentrant
        onlyParty
        whenFunded
    {
        Milestone storage m = _open(index);
        SettlementProposal memory p = _proposals[index];
        if (!p.active) revert NoActiveProposal();
        if (p.proposer == msg.sender) revert CannotAcceptOwnProposal();
        if (p.freelancerAmount != freelancerAmount || p.clientAmount != clientAmount) revert ProposalMismatch();
        emit MilestoneSettled(index, freelancerAmount, clientAmount);
        _close(index, m, MilestoneStatus.Settled, freelancerAmount, clientAmount);
    }

    // ------------------------------------------------------ 3. disputes
    /// @notice Freezes the milestone. Ordinary approval and settlement are impossible afterwards.
    function openDispute(uint256 index, bytes32 reasonHash) external nonReentrant onlyParty whenFunded {
        Milestone storage m = _open(index);
        m.status = MilestoneStatus.Disputed;
        m.disputedBy = msg.sender;
        delete _proposals[index];
        emit DisputeOpened(index, msg.sender, reasonHash);
    }

    /// @notice Admin splits the disputed milestone's own allocation. Executes once.
    function resolveDispute(uint256 index, uint256 freelancerAmount, uint256 clientAmount, bytes32 resolutionHash)
        external
        nonReentrant
        onlyAdmin
        whenFunded
    {
        if (index >= _milestones.length) revert InvalidMilestone();
        Milestone storage m = _milestones[index];
        if (m.status != MilestoneStatus.Disputed) revert InvalidStatus(m.status);
        _checkSum(m, freelancerAmount, clientAmount);
        emit DisputeResolved(index, msg.sender, freelancerAmount, clientAmount, resolutionHash);
        _close(index, m, MilestoneStatus.DisputeResolved, freelancerAmount, clientAmount);
    }

    // ------------------------------------------------------------- views
    function milestoneCount() external view returns (uint256) {
        return _milestones.length;
    }

    function getMilestone(uint256 index) external view returns (Milestone memory) {
        if (index >= _milestones.length) revert InvalidMilestone();
        return _milestones[index];
    }

    function getSettlementProposal(uint256 index) external view returns (SettlementProposal memory) {
        return _proposals[index];
    }

    /// @notice Real ETH held. Can exceed remainingEscrow() only if ETH was force-sent (selfdestruct); accounting never uses this value.
    function contractBalance() external view returns (uint256) {
        return address(this).balance;
    }

    /// @notice Accounting view: funds still owed to a milestone resolution.
    function remainingEscrow() public view returns (uint256) {
        if (!funded) return 0;
        return totalAmount - totalPaidToFreelancer - totalRefundedToClient;
    }

    function isCompleted() external view returns (bool) {
        return funded && unresolvedMilestones == 0;
    }

    // --------------------------------------------------------- internals
    function _open(uint256 index) private view returns (Milestone storage m) {
        if (index >= _milestones.length) revert InvalidMilestone();
        m = _milestones[index];
        if (m.status != MilestoneStatus.Open) revert InvalidStatus(m.status);
    }

    function _checkSum(Milestone storage m, uint256 f, uint256 c) private view {
        if (f + c != m.allocation) revert AmountsMustSumToAllocation(f + c, m.allocation);
    }

    /// @dev Checks-effects-interactions: all state is final before any ETH leaves.
    function _close(uint256 index, Milestone storage m, MilestoneStatus newStatus, uint256 f, uint256 c) private {
        m.status = newStatus;
        m.freelancerPaid = f;
        m.clientRefunded = c;
        totalPaidToFreelancer += f;
        totalRefundedToClient += c;
        unresolvedMilestones -= 1;
        delete _proposals[index];

        if (unresolvedMilestones == 0) emit EscrowCompleted();

        if (f > 0) payable(freelancer).sendValue(f);
        if (c > 0) payable(client).sendValue(c);
    }

    receive() external payable {
        revert DirectPaymentRejected();
    }
}
