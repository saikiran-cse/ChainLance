const { expect } = require("chai");
const { ethers } = require("hardhat");
const { loadFixture } = require("@nomicfoundation/hardhat-toolbox/network-helpers");

const eth = (v) => ethers.parseEther(v);
const ALLOCS = [eth("0.3"), eth("0.3"), eth("0.2")];
const TOTAL = eth("0.8");
const PROJECT_ID = ethers.id("agreement-1");
const HASH = ethers.id("some off-chain text");
const Status = { Open: 0n, Released: 1n, Settled: 2n, Disputed: 3n, DisputeResolved: 4n };

async function deployFixture() {
  const [admin, client, freelancer, other] = await ethers.getSigners();
  const factory = await ethers.deployContract("ChainLanceFactory", [admin.address]);
  await factory.connect(client).createEscrow(freelancer.address, PROJECT_ID, ALLOCS);
  const escrow = await ethers.getContractAt("ChainLanceEscrow", await factory.escrowOf(client.address, PROJECT_ID));
  return { admin, client, freelancer, other, factory, escrow };
}

async function fundedFixture() {
  const ctx = await deployFixture();
  await ctx.escrow.connect(ctx.client).fund({ value: TOTAL });
  return ctx;
}

describe("ChainLanceFactory", () => {
  it("creates an escrow with the caller as client and owner as admin", async () => {
    const { factory, escrow, client, freelancer, admin } = await loadFixture(deployFixture);
    expect(await escrow.client()).to.equal(client.address);
    expect(await escrow.freelancer()).to.equal(freelancer.address);
    expect(await escrow.admin()).to.equal(admin.address);
    expect(await escrow.totalAmount()).to.equal(TOTAL);
    expect(await escrow.milestoneCount()).to.equal(3n);
    expect(await factory.escrowCount()).to.equal(1n);
  });

  it("emits EscrowCreated", async () => {
    const { factory, client, freelancer } = await loadFixture(deployFixture);
    await expect(factory.connect(client).createEscrow(freelancer.address, ethers.id("p2"), ALLOCS)).to.emit(factory, "EscrowCreated");
  });

  it("rejects a duplicate project id", async () => {
    const { factory, client, freelancer } = await loadFixture(deployFixture);
    await expect(factory.connect(client).createEscrow(freelancer.address, PROJECT_ID, ALLOCS)).to.be.revertedWithCustomError(factory, "EscrowAlreadyExists");
  });

  it("rejects zero freelancer, zero project id, empty and zero allocations", async () => {
    const { factory, client, freelancer } = await loadFixture(deployFixture);
    const escrowAbi = await ethers.getContractFactory("ChainLanceEscrow");
    await expect(factory.connect(client).createEscrow(ethers.ZeroAddress, ethers.id("a"), ALLOCS)).to.be.revertedWithCustomError(escrowAbi, "ZeroAddress");
    await expect(factory.connect(client).createEscrow(freelancer.address, ethers.ZeroHash, ALLOCS)).to.be.revertedWithCustomError(escrowAbi, "InvalidProjectId");
    await expect(factory.connect(client).createEscrow(freelancer.address, ethers.id("b"), [])).to.be.revertedWithCustomError(escrowAbi, "InvalidMilestoneCount");
    await expect(factory.connect(client).createEscrow(freelancer.address, ethers.id("c"), [eth("1"), 0n])).to.be.revertedWithCustomError(escrowAbi, "ZeroAllocation");
    await expect(factory.connect(client).createEscrow(freelancer.address, ethers.id("d"), Array(21).fill(1n))).to.be.revertedWithCustomError(escrowAbi, "InvalidMilestoneCount");
  });

  it("rejects client == freelancer and admin as a party", async () => {
    const { factory, client, admin, freelancer } = await loadFixture(deployFixture);
    const escrowAbi = await ethers.getContractFactory("ChainLanceEscrow");
    await expect(factory.connect(client).createEscrow(client.address, ethers.id("e"), ALLOCS)).to.be.revertedWithCustomError(escrowAbi, "DuplicateParty");
    await expect(factory.connect(client).createEscrow(admin.address, ethers.id("f"), ALLOCS)).to.be.revertedWithCustomError(escrowAbi, "DuplicateParty");
    await expect(factory.connect(admin).createEscrow(freelancer.address, ethers.id("g"), ALLOCS)).to.be.revertedWithCustomError(escrowAbi, "DuplicateParty");
  });

  it("uses two-step admin transfer, affects only new escrows, and cannot be renounced", async () => {
    const { factory, escrow, admin, other, client, freelancer } = await loadFixture(deployFixture);
    await factory.connect(admin).transferOwnership(other.address);
    expect(await factory.owner()).to.equal(admin.address);
    await factory.connect(other).acceptOwnership();
    expect(await factory.owner()).to.equal(other.address);
    expect(await escrow.admin()).to.equal(admin.address); // existing escrow unchanged
    await factory.connect(client).createEscrow(freelancer.address, ethers.id("new"), ALLOCS);
    const e2 = await ethers.getContractAt("ChainLanceEscrow", await factory.escrowOf(client.address, ethers.id("new")));
    expect(await e2.admin()).to.equal(other.address);
    await expect(factory.connect(other).renounceOwnership()).to.be.revertedWithCustomError(factory, "RenounceDisabled");
    await expect(factory.connect(client).transferOwnership(client.address)).to.be.revertedWithCustomError(factory, "OwnableUnauthorizedAccount");
  });
});

describe("ChainLanceEscrow funding", () => {
  it("accepts exact funding from the client and emits Funded", async () => {
    const { escrow, client } = await loadFixture(deployFixture);
    await expect(escrow.connect(client).fund({ value: TOTAL })).to.emit(escrow, "Funded").withArgs(client.address, TOTAL);
    expect(await escrow.funded()).to.equal(true);
    expect(await escrow.contractBalance()).to.equal(TOTAL);
    expect(await escrow.remainingEscrow()).to.equal(TOTAL);
  });

  it("rejects under- and over-funding", async () => {
    const { escrow, client } = await loadFixture(deployFixture);
    await expect(escrow.connect(client).fund({ value: TOTAL - 1n })).to.be.revertedWithCustomError(escrow, "IncorrectFunding");
    await expect(escrow.connect(client).fund({ value: TOTAL + 1n })).to.be.revertedWithCustomError(escrow, "IncorrectFunding");
    await expect(escrow.connect(client).fund({ value: 0 })).to.be.revertedWithCustomError(escrow, "IncorrectFunding");
  });

  it("rejects funding by anyone except the client", async () => {
    const { escrow, freelancer, other, admin } = await loadFixture(deployFixture);
    for (const s of [freelancer, other, admin]) {
      await expect(escrow.connect(s).fund({ value: TOTAL })).to.be.revertedWithCustomError(escrow, "NotClient");
    }
  });

  it("rejects duplicate funding", async () => {
    const { escrow, client } = await loadFixture(fundedFixture);
    await expect(escrow.connect(client).fund({ value: TOTAL })).to.be.revertedWithCustomError(escrow, "AlreadyFunded");
  });

  it("rejects direct ETH transfers", async () => {
    const { escrow, client } = await loadFixture(deployFixture);
    await expect(client.sendTransaction({ to: await escrow.getAddress(), value: 1n })).to.be.revertedWithCustomError(escrow, "DirectPaymentRejected");
  });

  it("blocks every money operation before funding", async () => {
    const { escrow, client, freelancer, admin } = await loadFixture(deployFixture);
    await expect(escrow.connect(client).approveMilestone(0)).to.be.revertedWithCustomError(escrow, "NotFunded");
    await expect(escrow.connect(client).proposeSettlement(0, 0, ALLOCS[0])).to.be.revertedWithCustomError(escrow, "NotFunded");
    await expect(escrow.connect(freelancer).openDispute(0, HASH)).to.be.revertedWithCustomError(escrow, "NotFunded");
    await expect(escrow.connect(admin).resolveDispute(0, 0, ALLOCS[0], HASH)).to.be.revertedWithCustomError(escrow, "NotFunded");
  });
});

describe("ChainLanceEscrow milestone approval", () => {
  it("pays exactly the allocation to the freelancer", async () => {
    const { escrow, client, freelancer } = await loadFixture(fundedFixture);
    // Send once, then run each matcher against the same transaction
    // (Hardhat does not allow chaining changeEtherBalances after emit).
    const tx = await escrow.connect(client).approveMilestone(0);
    await expect(tx).to.emit(escrow, "MilestoneReleased").withArgs(0, freelancer.address, ALLOCS[0]);
    await expect(tx).to.changeEtherBalances([freelancer, escrow], [ALLOCS[0], -ALLOCS[0]]);
    const m = await escrow.getMilestone(0);
    expect(m.status).to.equal(Status.Released);
    expect(m.freelancerPaid).to.equal(ALLOCS[0]);
    expect(await escrow.remainingEscrow()).to.equal(TOTAL - ALLOCS[0]);
    expect(await escrow.unresolvedMilestones()).to.equal(2n);
  });

  it("rejects non-clients, bad indices and double payout", async () => {
    const { escrow, client, freelancer, other, admin } = await loadFixture(fundedFixture);
    for (const s of [freelancer, other, admin]) {
      await expect(escrow.connect(s).approveMilestone(0)).to.be.revertedWithCustomError(escrow, "NotClient");
    }
    await expect(escrow.connect(client).approveMilestone(3)).to.be.revertedWithCustomError(escrow, "InvalidMilestone");
    await escrow.connect(client).approveMilestone(0);
    await expect(escrow.connect(client).approveMilestone(0)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
  });

  it("completes the escrow and leaves a zero balance after all milestones", async () => {
    const { escrow, client } = await loadFixture(fundedFixture);
    await escrow.connect(client).approveMilestone(0);
    await escrow.connect(client).approveMilestone(1);
    await expect(escrow.connect(client).approveMilestone(2)).to.emit(escrow, "EscrowCompleted");
    expect(await escrow.isCompleted()).to.equal(true);
    expect(await escrow.contractBalance()).to.equal(0n);
    expect(await escrow.totalPaidToFreelancer()).to.equal(TOTAL);
  });

  it("lets milestones be approved in any order", async () => {
    const { escrow, client } = await loadFixture(fundedFixture);
    await escrow.connect(client).approveMilestone(2);
    expect((await escrow.getMilestone(2)).status).to.equal(Status.Released);
    expect((await escrow.getMilestone(0)).status).to.equal(Status.Open);
  });
});

describe("ChainLanceEscrow agreed settlement (late work / refund)", () => {
  const f = eth("0.2");
  const c = eth("0.1");

  it("executes an exact split once the other party accepts", async () => {
    const { escrow, client, freelancer } = await loadFixture(fundedFixture);
    await expect(escrow.connect(client).proposeSettlement(0, f, c)).to.emit(escrow, "SettlementProposed");
    const tx = await escrow.connect(freelancer).acceptSettlement(0, f, c);
    await expect(tx).to.emit(escrow, "MilestoneSettled").withArgs(0, f, c);
    await expect(tx).to.changeEtherBalances([freelancer, client, escrow], [f, c, -(f + c)]);
    const m = await escrow.getMilestone(0);
    expect(m.status).to.equal(Status.Settled);
    expect(m.freelancerPaid).to.equal(f);
    expect(m.clientRefunded).to.equal(c);
    expect(await escrow.totalRefundedToClient()).to.equal(c);
    expect(await escrow.remainingEscrow()).to.equal(TOTAL - ALLOCS[0]);
  });

  it("supports a freelancer-proposed full refund", async () => {
    const { escrow, client, freelancer } = await loadFixture(fundedFixture);
    await escrow.connect(freelancer).proposeSettlement(1, 0, ALLOCS[1]);
    await expect(escrow.connect(client).acceptSettlement(1, 0, ALLOCS[1])).to.changeEtherBalances([client, freelancer], [ALLOCS[1], 0n]);
  });

  it("rejects amounts that do not sum exactly to the allocation", async () => {
    const { escrow, client } = await loadFixture(fundedFixture);
    await expect(escrow.connect(client).proposeSettlement(0, f, c + 1n)).to.be.revertedWithCustomError(escrow, "AmountsMustSumToAllocation");
    await expect(escrow.connect(client).proposeSettlement(0, f, c - 1n)).to.be.revertedWithCustomError(escrow, "AmountsMustSumToAllocation");
    await expect(escrow.connect(client).proposeSettlement(0, ALLOCS[0] + 1n, 0)).to.be.revertedWithCustomError(escrow, "AmountsMustSumToAllocation");
  });

  it("rejects outsiders, self-acceptance, missing and mismatched proposals", async () => {
    const { escrow, client, freelancer, other, admin } = await loadFixture(fundedFixture);
    await expect(escrow.connect(other).proposeSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "NotParty");
    await expect(escrow.connect(admin).proposeSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "NotParty");
    await expect(escrow.connect(freelancer).acceptSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "NoActiveProposal");
    await escrow.connect(client).proposeSettlement(0, f, c);
    await expect(escrow.connect(client).acceptSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "CannotAcceptOwnProposal");
    await expect(escrow.connect(other).acceptSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "NotParty");
    await expect(escrow.connect(freelancer).acceptSettlement(0, ALLOCS[0], 0)).to.be.revertedWithCustomError(escrow, "ProposalMismatch");
  });

  it("lets only the proposer withdraw, and a withdrawn proposal cannot be accepted", async () => {
    const { escrow, client, freelancer } = await loadFixture(fundedFixture);
    await escrow.connect(client).proposeSettlement(0, f, c);
    await expect(escrow.connect(freelancer).withdrawSettlement(0)).to.be.revertedWithCustomError(escrow, "NotProposer");
    await expect(escrow.connect(client).withdrawSettlement(0)).to.emit(escrow, "SettlementWithdrawn");
    await expect(escrow.connect(freelancer).acceptSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "NoActiveProposal");
  });

  it("a replaced proposal cannot be accepted with the old amounts", async () => {
    const { escrow, client, freelancer } = await loadFixture(fundedFixture);
    await escrow.connect(client).proposeSettlement(0, f, c);
    await escrow.connect(client).proposeSettlement(0, eth("0.1"), eth("0.2"));
    await expect(escrow.connect(freelancer).acceptSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "ProposalMismatch");
  });

  it("cannot settle twice or after approval", async () => {
    const { escrow, client, freelancer } = await loadFixture(fundedFixture);
    await escrow.connect(client).proposeSettlement(0, f, c);
    await escrow.connect(freelancer).acceptSettlement(0, f, c);
    await expect(escrow.connect(freelancer).acceptSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
    await expect(escrow.connect(client).approveMilestone(0)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
    await expect(escrow.connect(client).proposeSettlement(0, f, c)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
  });
});

describe("ChainLanceEscrow disputes", () => {
  it("lets either party open a dispute and freezes ordinary operations", async () => {
    const { escrow, client, freelancer } = await loadFixture(fundedFixture);
    await expect(escrow.connect(freelancer).openDispute(0, HASH)).to.emit(escrow, "DisputeOpened").withArgs(0, freelancer.address, HASH);
    const m = await escrow.getMilestone(0);
    expect(m.status).to.equal(Status.Disputed);
    expect(m.disputedBy).to.equal(freelancer.address);
    await expect(escrow.connect(client).approveMilestone(0)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
    await expect(escrow.connect(client).proposeSettlement(0, 0, ALLOCS[0])).to.be.revertedWithCustomError(escrow, "InvalidStatus");
    await expect(escrow.connect(freelancer).openDispute(0, HASH)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
    // other milestones remain unaffected
    await expect(escrow.connect(client).approveMilestone(1)).to.not.be.reverted;
  });

  it("clears a pending settlement proposal when a dispute opens", async () => {
    const { escrow, client, freelancer } = await loadFixture(fundedFixture);
    await escrow.connect(client).proposeSettlement(0, eth("0.1"), eth("0.2"));
    await escrow.connect(client).openDispute(0, HASH);
    expect((await escrow.getSettlementProposal(0)).active).to.equal(false);
    await expect(escrow.connect(freelancer).acceptSettlement(0, eth("0.1"), eth("0.2"))).to.be.revertedWithCustomError(escrow, "InvalidStatus");
  });

  it("rejects outsiders opening a dispute", async () => {
    const { escrow, other, admin } = await loadFixture(fundedFixture);
    await expect(escrow.connect(other).openDispute(0, HASH)).to.be.revertedWithCustomError(escrow, "NotParty");
    await expect(escrow.connect(admin).openDispute(0, HASH)).to.be.revertedWithCustomError(escrow, "NotParty");
  });

  it("lets only the admin resolve, with an exact split, exactly once", async () => {
    const { escrow, client, freelancer, admin, other } = await loadFixture(fundedFixture);
    await escrow.connect(client).openDispute(0, HASH);
    const f = eth("0.1");
    const c = eth("0.2");
    for (const s of [client, freelancer, other]) {
      await expect(escrow.connect(s).resolveDispute(0, f, c, HASH)).to.be.revertedWithCustomError(escrow, "NotAdmin");
    }
    await expect(escrow.connect(admin).resolveDispute(0, f, c + 1n, HASH)).to.be.revertedWithCustomError(escrow, "AmountsMustSumToAllocation");
    await expect(escrow.connect(admin).resolveDispute(0, ALLOCS[0] + 1n, 0, HASH)).to.be.revertedWithCustomError(escrow, "AmountsMustSumToAllocation");
    const tx = await escrow.connect(admin).resolveDispute(0, f, c, HASH);
    await expect(tx).to.emit(escrow, "DisputeResolved").withArgs(0, admin.address, f, c, HASH);
    await expect(tx).to.changeEtherBalances([freelancer, client, admin, escrow], [f, c, 0n, -(f + c)]);
    expect((await escrow.getMilestone(0)).status).to.equal(Status.DisputeResolved);
    await expect(escrow.connect(admin).resolveDispute(0, f, c, HASH)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
  });

  it("admin cannot resolve open, released or settled milestones, or bad indices", async () => {
    const { escrow, client, admin } = await loadFixture(fundedFixture);
    await expect(escrow.connect(admin).resolveDispute(0, ALLOCS[0], 0, HASH)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
    await escrow.connect(client).approveMilestone(0);
    await expect(escrow.connect(admin).resolveDispute(0, 0, ALLOCS[0], HASH)).to.be.revertedWithCustomError(escrow, "InvalidStatus");
    await expect(escrow.connect(admin).resolveDispute(9, 0, 1, HASH)).to.be.revertedWithCustomError(escrow, "InvalidMilestone");
  });

  it("admin has no other way to move funds and gains no balance", async () => {
    const { escrow, admin } = await loadFixture(fundedFixture);
    const names = escrow.interface.fragments.filter((x) => x.type === "function").map((x) => x.name);
    for (const bad of ["withdraw", "sweep", "emergencyWithdraw", "setAdmin"]) expect(names).to.not.include(bad);
    await expect(escrow.connect(admin).approveMilestone(0)).to.be.revertedWithCustomError(escrow, "NotClient");
    await expect(escrow.connect(admin).fund({ value: TOTAL })).to.be.revertedWithCustomError(escrow, "NotClient");
  });

  it("supports a full refund resolution", async () => {
    const { escrow, client, freelancer, admin } = await loadFixture(fundedFixture);
    await escrow.connect(freelancer).openDispute(2, HASH);
    await expect(escrow.connect(admin).resolveDispute(2, 0, ALLOCS[2], HASH)).to.changeEtherBalances([client, freelancer], [ALLOCS[2], 0n]);
  });
});

describe("ChainLanceEscrow accounting and security", () => {
  it("never pays out more than was deposited across a mixed lifecycle", async () => {
    const { escrow, client, freelancer, admin } = await loadFixture(fundedFixture);
    await escrow.connect(client).approveMilestone(0);
    await escrow.connect(client).proposeSettlement(1, eth("0.15"), eth("0.15"));
    await escrow.connect(freelancer).acceptSettlement(1, eth("0.15"), eth("0.15"));
    await escrow.connect(freelancer).openDispute(2, HASH);
    await escrow.connect(admin).resolveDispute(2, eth("0.05"), eth("0.15"), HASH);
    expect(await escrow.totalPaidToFreelancer()).to.equal(eth("0.3") + eth("0.15") + eth("0.05"));
    expect(await escrow.totalRefundedToClient()).to.equal(eth("0.15") + eth("0.15"));
    expect((await escrow.totalPaidToFreelancer()) + (await escrow.totalRefundedToClient())).to.equal(TOTAL);
    expect(await escrow.contractBalance()).to.equal(0n);
    expect(await escrow.remainingEscrow()).to.equal(0n);
    expect(await escrow.isCompleted()).to.equal(true);
  });

  it("blocks reentrancy from a malicious freelancer contract", async () => {
    const [admin, client] = await ethers.getSigners();
    const attacker = await ethers.deployContract("ReentrantFreelancer");
    const factory = await ethers.deployContract("ChainLanceFactory", [admin.address]);
    await factory.connect(client).createEscrow(await attacker.getAddress(), ethers.id("evil"), ALLOCS);
    const escrow = await ethers.getContractAt("ChainLanceEscrow", await factory.escrowOf(client.address, ethers.id("evil")));
    await escrow.connect(client).fund({ value: TOTAL });
    await attacker.setEscrow(await escrow.getAddress());

    await expect(escrow.connect(client).approveMilestone(0)).to.changeEtherBalance(attacker, ALLOCS[0]);

    expect(await attacker.reentryAttempted()).to.equal(true);
    expect(await attacker.reentryBlocked()).to.equal(true);
    expect(await attacker.lastSelector()).to.equal(escrow.interface.getError("ReentrancyGuardReentrantCall").selector);
    expect(await escrow.totalPaidToFreelancer()).to.equal(ALLOCS[0]);
    expect(await escrow.contractBalance()).to.equal(TOTAL - ALLOCS[0]);
  });

  it("view functions reject out-of-range milestones", async () => {
    const { escrow } = await loadFixture(fundedFixture);
    await expect(escrow.getMilestone(3)).to.be.revertedWithCustomError(escrow, "InvalidMilestone");
  });
});
