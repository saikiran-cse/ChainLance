// Stage 5 hardening tests. They add to, and never replace, test/ChainLanceEscrow.test.js.
const { expect } = require("chai");
const { ethers } = require("hardhat");
const { loadFixture } = require("@nomicfoundation/hardhat-toolbox/network-helpers");

const eth = (v) => ethers.parseEther(v);
const ALLOCS = [eth("0.3"), eth("0.3"), eth("0.2")];
const TOTAL = eth("0.8");
const HASH = ethers.id("some off-chain text");
const Status = { Open: 0n, Released: 1n, Settled: 2n, Disputed: 3n, DisputeResolved: 4n };

let counter = 0;
const nextId = (label) => ethers.id(`${label}-${++counter}`);
const sum = (values) => values.reduce((a, b) => a + b, 0n);

async function baseFixture() {
  const [admin, client, freelancer, other] = await ethers.getSigners();
  const factory = await ethers.deployContract("ChainLanceFactory", [admin.address]);
  return { admin, client, freelancer, other, factory };
}

/** Creates an escrow as `creator` (default: the client) and, by default, funds it in full as the client. */
async function newEscrow(ctx, { freelancerAddr = ctx.freelancer.address, allocs = ALLOCS, fund = true, label = "p" } = {}) {
  const id = nextId(label);
  await ctx.factory.connect(ctx.client).createEscrow(freelancerAddr, id, allocs);
  const address = await ctx.factory.escrowOf(ctx.client.address, id);
  const escrow = await ethers.getContractAt("ChainLanceEscrow", address);
  if (fund) await escrow.connect(ctx.client).fund({ value: sum(allocs) });
  return { escrow, id };
}

// ------------------------------------------------------------------------------------------------
describe("Stage 5: factory hardening", () => {
  it("scopes duplicate protection to the caller so a front-runner cannot squat a project id", async () => {
    const { factory, client, freelancer, other } = await loadFixture(baseFixture);
    const id = ethers.id("agreement-7");

    // an attacker copies the public call parameters and gets in first
    await factory.connect(other).createEscrow(freelancer.address, id, ALLOCS);
    // the real client is NOT blocked
    await expect(factory.connect(client).createEscrow(freelancer.address, id, ALLOCS)).to.emit(factory, "EscrowCreated");

    const squatted = await factory.escrowOf(other.address, id);
    const real = await factory.escrowOf(client.address, id);
    expect(squatted).to.not.equal(ethers.ZeroAddress);
    expect(real).to.not.equal(ethers.ZeroAddress);
    expect(real).to.not.equal(squatted);

    const realEscrow = await ethers.getContractAt("ChainLanceEscrow", real);
    const fakeEscrow = await ethers.getContractAt("ChainLanceEscrow", squatted);
    expect(await realEscrow.client()).to.equal(client.address);
    expect(await realEscrow.projectId()).to.equal(id);
    expect(await fakeEscrow.client()).to.equal(other.address); // a backend must reject this one by its client
    expect(await factory.escrowCount()).to.equal(2n);
  });

  it("still rejects the same client reusing a project id, whatever the other parameters are", async () => {
    const { factory, client, freelancer, other } = await loadFixture(baseFixture);
    const id = ethers.id("agreement-8");
    await factory.connect(client).createEscrow(freelancer.address, id, ALLOCS);

    await expect(factory.connect(client).createEscrow(freelancer.address, id, ALLOCS))
      .to.be.revertedWithCustomError(factory, "EscrowAlreadyExists").withArgs(id);
    await expect(factory.connect(client).createEscrow(other.address, id, [eth("1")]))
      .to.be.revertedWithCustomError(factory, "EscrowAlreadyExists").withArgs(id);
    expect(await factory.escrowCount()).to.equal(1n);
  });

  it("returns the zero address for a client and project id pair that was never created", async () => {
    const { factory, client, freelancer, other } = await loadFixture(baseFixture);
    const id = ethers.id("agreement-9");
    expect(await factory.escrowOf(client.address, id)).to.equal(ethers.ZeroAddress);
    await factory.connect(client).createEscrow(freelancer.address, id, ALLOCS);
    expect(await factory.escrowOf(other.address, id)).to.equal(ethers.ZeroAddress);
    expect(await factory.escrowOf(client.address, ethers.id("another"))).to.equal(ethers.ZeroAddress);
  });

  it("pins the project id encoding the backend must use: keccak256(abi.encodePacked(uint256 agreementId, bytes32 snapshotHash))", async () => {
    const { factory, client, freelancer } = await loadFixture(baseFixture);
    // Vector computed by an independent Keccak-256 implementation (not by ethers).
    const snapshotHash = ethers.id("example snapshot");
    const expected = "0x1faee1570066771b6227a97d8cc3c551e64611a51ef9d899a0044049f61fc380";
    expect(snapshotHash).to.equal("0x411bc73d9ea3b5244542429d99a38608950885df0f5fec423c44bbe7e84cc6c4");

    expect(ethers.solidityPackedKeccak256(["uint256", "bytes32"], [42n, snapshotHash])).to.equal(expected);
    // the same thing spelled out as bytes: 32-byte big-endian id, then the 32-byte hash
    expect(ethers.keccak256(ethers.concat([ethers.zeroPadValue(ethers.toBeHex(42n), 32), snapshotHash]))).to.equal(expected);
    // the tempting alternative (decimal text) is a DIFFERENT value
    expect(ethers.keccak256(ethers.concat([ethers.toUtf8Bytes("42"), snapshotHash]))).to.not.equal(expected);

    // the factory stores the id unchanged
    await factory.connect(client).createEscrow(freelancer.address, expected, ALLOCS);
    const escrow = await ethers.getContractAt("ChainLanceEscrow", await factory.escrowOf(client.address, expected));
    expect(await escrow.projectId()).to.equal(expected);
  });

  it("rejects allocations whose sum overflows uint256", async () => {
    const { factory, client, freelancer } = await loadFixture(baseFixture);
    await expect(factory.connect(client).createEscrow(freelancer.address, nextId("overflow"), [2n ** 255n, 2n ** 255n]))
      .to.be.revertedWithPanic(0x11);
  });
});

// ------------------------------------------------------------------------------------------------
describe("Stage 5: failed transfers are atomic", () => {
  it("approval to a freelancer that cannot receive ETH reverts and changes nothing", async () => {
    const ctx = await loadFixture(baseFixture);
    const rejecter = await ethers.deployContract("EthRejecter");
    const { escrow } = await newEscrow(ctx, { freelancerAddr: await rejecter.getAddress() });

    await expect(escrow.connect(ctx.client).approveMilestone(0)).to.be.reverted;

    const m = await escrow.getMilestone(0);
    expect(m.status).to.equal(Status.Open);
    expect(m.freelancerPaid).to.equal(0n);
    expect(await escrow.unresolvedMilestones()).to.equal(3n);
    expect(await escrow.totalPaidToFreelancer()).to.equal(0n);
    expect(await escrow.contractBalance()).to.equal(TOTAL);
    expect(await escrow.remainingEscrow()).to.equal(TOTAL);
  });

  it("a refund-only resolution still works when the freelancer cannot receive ETH", async () => {
    const ctx = await loadFixture(baseFixture);
    const { client, admin } = ctx;
    const rejecter = await ethers.deployContract("EthRejecter");
    const { escrow } = await newEscrow(ctx, { freelancerAddr: await rejecter.getAddress() });

    await escrow.connect(client).openDispute(0, HASH);
    await expect(escrow.connect(admin).resolveDispute(0, ALLOCS[0], 0n, HASH)).to.be.reverted; // paying the rejecter fails
    expect((await escrow.getMilestone(0)).status).to.equal(Status.Disputed); // nothing was recorded

    await expect(escrow.connect(admin).resolveDispute(0, 0n, ALLOCS[0], HASH)).to.changeEtherBalance(client, ALLOCS[0]);
    expect((await escrow.getMilestone(0)).status).to.equal(Status.DisputeResolved);
    expect(await escrow.contractBalance()).to.equal(TOTAL - ALLOCS[0]);
  });

  it("a client contract that rejects ETH blocks only refunds, atomically, and works again once it accepts", async () => {
    const ctx = await loadFixture(baseFixture);
    const { factory, freelancer, admin, other } = ctx;
    const rc = await ethers.deployContract("RejectingClient");
    const rcAddress = await rc.getAddress();
    const id = nextId("rejecting-client");

    await rc.connect(other).createEscrow(await factory.getAddress(), freelancer.address, id, ALLOCS);
    const escrowAddress = await factory.escrowOf(rcAddress, id);
    const escrow = await ethers.getContractAt("ChainLanceEscrow", escrowAddress);
    expect(await escrow.client()).to.equal(rcAddress);

    await rc.connect(other).fund(escrowAddress, { value: TOTAL });
    await rc.connect(other).openDispute(escrowAddress, 0, HASH);
    await rc.connect(other).openDispute(escrowAddress, 1, HASH);
    await rc.setRejectPayments(true);

    // a split with a refund share fails as a whole: nothing is recorded, nothing is paid
    await expect(escrow.connect(admin).resolveDispute(0, ALLOCS[0] / 2n, ALLOCS[0] / 2n, HASH)).to.be.reverted;
    expect((await escrow.getMilestone(0)).status).to.equal(Status.Disputed);
    expect(await escrow.contractBalance()).to.equal(TOTAL);

    // paying the freelancer in full needs no transfer to the client, so it still works
    await expect(escrow.connect(admin).resolveDispute(0, ALLOCS[0], 0n, HASH)).to.changeEtherBalance(freelancer, ALLOCS[0]);

    // once the client accepts ETH again, the split works
    await rc.setRejectPayments(false);
    await expect(escrow.connect(admin).resolveDispute(1, ALLOCS[1] / 2n, ALLOCS[1] / 2n, HASH))
      .to.changeEtherBalances([freelancer, rc], [ALLOCS[1] / 2n, ALLOCS[1] / 2n]);
    expect(await escrow.contractBalance()).to.equal(ALLOCS[2]);
  });

  it("ETH force-sent into the escrow never enters the accounting and cannot be withdrawn", async () => {
    const ctx = await loadFixture(baseFixture);
    const { client } = ctx;
    const { escrow } = await newEscrow(ctx, { fund: false });

    const ForceSend = await ethers.getContractFactory("ForceSend");
    const forced = await ForceSend.deploy(await escrow.getAddress(), { value: eth("1") });
    await forced.deploymentTransaction().wait();

    expect(await escrow.contractBalance()).to.equal(eth("1"));
    expect(await escrow.remainingEscrow()).to.equal(0n); // not funded yet
    await escrow.connect(client).fund({ value: TOTAL }); // exact funding still works: it checks msg.value, not the balance
    expect(await escrow.remainingEscrow()).to.equal(TOTAL);
    expect(await escrow.contractBalance()).to.equal(TOTAL + eth("1"));

    for (let i = 0; i < 3; i++) await escrow.connect(client).approveMilestone(i);

    expect(await escrow.isCompleted()).to.equal(true);
    expect(await escrow.totalPaidToFreelancer()).to.equal(TOTAL);
    expect(await escrow.remainingEscrow()).to.equal(0n);
    expect(await escrow.contractBalance()).to.equal(eth("1")); // the forced ETH stays; no function can move it
  });
});

// ------------------------------------------------------------------------------------------------
describe("Stage 5: milestone state machine", () => {
  const reach = {
    Released: async (e, c) => {
      await e.connect(c.client).approveMilestone(0);
    },
    Settled: async (e, c) => {
      await e.connect(c.client).proposeSettlement(0, eth("0.1"), eth("0.2"));
      await e.connect(c.freelancer).acceptSettlement(0, eth("0.1"), eth("0.2"));
    },
    Disputed: async (e, c) => {
      await e.connect(c.freelancer).openDispute(0, HASH);
    },
    DisputeResolved: async (e, c) => {
      await e.connect(c.client).openDispute(0, HASH);
      await e.connect(c.admin).resolveDispute(0, eth("0.1"), eth("0.2"), HASH);
    },
  };

  for (const name of Object.keys(reach)) {
    it(`a milestone in state ${name} refuses every ordinary operation and leaves the others working`, async () => {
      const ctx = await loadFixture(baseFixture);
      const { client, freelancer, admin } = ctx;
      const { escrow } = await newEscrow(ctx);
      await reach[name](escrow, ctx);

      const S = Status[name];
      expect((await escrow.getMilestone(0)).status).to.equal(S);
      const a = eth("0.1");
      const b = eth("0.2");

      await expect(escrow.connect(client).approveMilestone(0)).to.be.revertedWithCustomError(escrow, "InvalidStatus").withArgs(S);
      await expect(escrow.connect(client).proposeSettlement(0, a, b)).to.be.revertedWithCustomError(escrow, "InvalidStatus").withArgs(S);
      await expect(escrow.connect(freelancer).proposeSettlement(0, a, b)).to.be.revertedWithCustomError(escrow, "InvalidStatus").withArgs(S);
      await expect(escrow.connect(freelancer).acceptSettlement(0, a, b)).to.be.revertedWithCustomError(escrow, "InvalidStatus").withArgs(S);
      await expect(escrow.connect(client).openDispute(0, HASH)).to.be.revertedWithCustomError(escrow, "InvalidStatus").withArgs(S);
      await expect(escrow.connect(freelancer).openDispute(0, HASH)).to.be.revertedWithCustomError(escrow, "InvalidStatus").withArgs(S);
      if (name !== "Disputed") {
        await expect(escrow.connect(admin).resolveDispute(0, a, b, HASH)).to.be.revertedWithCustomError(escrow, "InvalidStatus").withArgs(S);
      }
      await expect(escrow.connect(client).withdrawSettlement(0)).to.be.revertedWithCustomError(escrow, "NoActiveProposal");
      await expect(escrow.connect(freelancer).withdrawSettlement(0)).to.be.revertedWithCustomError(escrow, "NoActiveProposal");

      // milestone 1 is untouched and still pays exactly its own allocation
      expect((await escrow.getMilestone(1)).status).to.equal(Status.Open);
      await expect(escrow.connect(client).approveMilestone(1)).to.changeEtherBalance(freelancer, ALLOCS[1]);
    });
  }
});

// ------------------------------------------------------------------------------------------------
describe("Stage 5: events", () => {
  const countLogs = (receipt, topic) => receipt.logs.filter((log) => log.topics[0] === topic).length;

  it("emits EscrowCompleted only with the last resolution", async () => {
    const ctx = await loadFixture(baseFixture);
    const { client, freelancer, admin } = ctx;
    const { escrow } = await newEscrow(ctx);
    const topic = escrow.interface.getEvent("EscrowCompleted").topicHash;

    const first = await (await escrow.connect(client).approveMilestone(0)).wait();
    await escrow.connect(client).proposeSettlement(1, eth("0.15"), eth("0.15"));
    const second = await (await escrow.connect(freelancer).acceptSettlement(1, eth("0.15"), eth("0.15"))).wait();
    await escrow.connect(freelancer).openDispute(2, HASH);
    const third = await (await escrow.connect(admin).resolveDispute(2, eth("0.05"), eth("0.15"), HASH)).wait();

    expect([countLogs(first, topic), countLogs(second, topic), countLogs(third, topic)]).to.deep.equal([0, 0, 1]);
  });

  it("emits EscrowCompleted exactly once when the last milestone is closed by any of the three paths", async () => {
    const ctx = await loadFixture(baseFixture);
    const { client, freelancer, admin } = ctx;
    const paths = {
      approve: async (e) => (await e.connect(client).approveMilestone(0)).wait(),
      settle: async (e) => {
        await e.connect(freelancer).proposeSettlement(0, eth("0.4"), eth("0.6"));
        return (await e.connect(client).acceptSettlement(0, eth("0.4"), eth("0.6"))).wait();
      },
      resolve: async (e) => {
        await e.connect(client).openDispute(0, HASH);
        return (await e.connect(admin).resolveDispute(0, eth("1"), 0n, HASH)).wait();
      },
    };
    for (const name of Object.keys(paths)) {
      const { escrow } = await newEscrow(ctx, { allocs: [eth("1")], label: name });
      const topic = escrow.interface.getEvent("EscrowCompleted").topicHash;
      const receipt = await paths[name](escrow);
      expect(countLogs(receipt, topic), name).to.equal(1);
      expect(await escrow.isCompleted(), name).to.equal(true);
    }
  });
});

// ------------------------------------------------------------------------------------------------
describe("Stage 5: accounting invariants", () => {
  function makeRng(seed) {
    let s = seed >>> 0;
    return () => {
      s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
      return s / 4294967296;
    };
  }
  const pick = (rng, n) => Math.floor(rng() * n);

  async function checkInvariants(escrow, allocs) {
    const total = sum(allocs);
    const paid = await escrow.totalPaidToFreelancer();
    const refunded = await escrow.totalRefundedToClient();
    const remaining = await escrow.remainingEscrow();
    expect(paid + refunded + remaining).to.equal(total);
    expect(await escrow.contractBalance()).to.equal(remaining);

    let resolvedAllocation = 0n;
    let unresolved = 0n;
    let sumPaid = 0n;
    let sumRefunded = 0n;
    for (let i = 0; i < allocs.length; i++) {
      const m = await escrow.getMilestone(i);
      expect(m.allocation).to.equal(allocs[i]);
      const done = m.status === Status.Released || m.status === Status.Settled || m.status === Status.DisputeResolved;
      if (done) {
        expect(m.freelancerPaid + m.clientRefunded).to.equal(m.allocation); // never more, never less than its own allocation
        resolvedAllocation += m.allocation;
        sumPaid += m.freelancerPaid;
        sumRefunded += m.clientRefunded;
      } else {
        expect(m.freelancerPaid).to.equal(0n);
        expect(m.clientRefunded).to.equal(0n);
        unresolved += 1n;
      }
    }
    expect(await escrow.unresolvedMilestones()).to.equal(unresolved);
    expect(sumPaid).to.equal(paid);
    expect(sumRefunded).to.equal(refunded);
    expect(resolvedAllocation).to.equal(paid + refunded);
    expect(remaining).to.equal(total - resolvedAllocation);
  }

  it("keeps the accounting exact across many random lifecycles on all three payment paths", async () => {
    const ctx = await loadFixture(baseFixture);
    const { client, freelancer, admin } = ctx;
    const rng = makeRng(20261023); // fixed seed: the same sequence every run

    for (let run = 0; run < 12; run++) {
      const n = 2 + pick(rng, 5); // 2 to 6 milestones
      const allocs = Array.from({ length: n }, () => BigInt(1 + pick(rng, 1000)) * 10n ** 12n + BigInt(pick(rng, 1000)));
      const { escrow } = await newEscrow(ctx, { allocs, label: "random" });
      await checkInvariants(escrow, allocs);

      const open = [...Array(n).keys()];
      while (open.length > 0) {
        const slot = pick(rng, open.length);
        const i = open[slot];
        const alloc = allocs[i];
        const proposer = [client, freelancer][pick(rng, 2)];
        const responder = proposer === client ? freelancer : client;
        const f = [0n, alloc, (alloc * BigInt(pick(rng, 1000))) / 1000n][pick(rng, 3)];
        const c = alloc - f;

        switch (pick(rng, 5)) {
          case 0: // client approves
            await escrow.connect(client).approveMilestone(i);
            open.splice(slot, 1);
            break;
          case 1: // agreed settlement
            await escrow.connect(proposer).proposeSettlement(i, f, c);
            await escrow.connect(responder).acceptSettlement(i, f, c);
            open.splice(slot, 1);
            break;
          case 2: // dispute, then admin split
            await escrow.connect(proposer).openDispute(i, HASH);
            await escrow.connect(admin).resolveDispute(i, f, c, HASH);
            open.splice(slot, 1);
            break;
          case 3: // proposal withdrawn: the milestone stays open
            await escrow.connect(proposer).proposeSettlement(i, f, c);
            await escrow.connect(proposer).withdrawSettlement(i);
            break;
          default: // proposal replaced by the other party's, which the first party then accepts
            await escrow.connect(proposer).proposeSettlement(i, f, c);
            await escrow.connect(responder).proposeSettlement(i, c, f);
            await escrow.connect(proposer).acceptSettlement(i, c, f);
            open.splice(slot, 1);
        }
        await checkInvariants(escrow, allocs);
      }

      expect(await escrow.isCompleted()).to.equal(true);
      expect(await escrow.contractBalance()).to.equal(0n);
    }
  });
});
