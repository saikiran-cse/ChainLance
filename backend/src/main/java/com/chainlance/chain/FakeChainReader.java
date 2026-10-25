package com.chainlance.chain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class FakeChainReader implements ChainReader {

    private long currentBlockNumber = 100L;
    private BigInteger chainId = BigInteger.valueOf(31337L);

    private final Map<String, TransactionReceipt> receipts = new HashMap<>();
    private final Map<String, Integer> milestoneCounts = new HashMap<>();
    private final Map<String, Map<Integer, EscrowMilestoneView>> milestones = new HashMap<>();
    private final Map<String, Boolean> fundedStates = new HashMap<>();
    private final Map<String, Boolean> completedStates = new HashMap<>();
    private final Map<String, String> clients = new HashMap<>();
    private final Map<String, String> freelancers = new HashMap<>();
    private final Map<String, String> admins = new HashMap<>();
    private final Map<String, BigInteger> totalAmounts = new HashMap<>();
    private final Map<String, String> projectIds = new HashMap<>();
    private final Map<String, String> factoryEscrows = new HashMap<>();

    private final Web3jChainReader delegateDecoder = new Web3jChainReader(null);

    public void setCurrentBlockNumber(long currentBlockNumber) {
        this.currentBlockNumber = currentBlockNumber;
    }

    public void setChainId(BigInteger chainId) {
        this.chainId = chainId;
    }

    public void putReceipt(String txHash, TransactionReceipt receipt) {
        receipts.put(txHash.toLowerCase(), receipt);
    }

    public void setupEscrow(String escrowAddress, String client, String freelancer, String admin,
                            String projectId, BigInteger totalAmount, List<BigInteger> allocations) {
        String addr = escrowAddress.toLowerCase();
        clients.put(addr, client.toLowerCase());
        freelancers.put(addr, freelancer.toLowerCase());
        admins.put(addr, admin.toLowerCase());
        projectIds.put(addr, projectId.toLowerCase());
        totalAmounts.put(addr, totalAmount);
        fundedStates.put(addr, false);
        completedStates.put(addr, false);

        milestoneCounts.put(addr, allocations.size());
        Map<Integer, EscrowMilestoneView> mViews = new HashMap<>();
        for (int i = 0; i < allocations.size(); i++) {
            mViews.put(i, new EscrowMilestoneView(allocations.get(i), 0, BigInteger.ZERO, BigInteger.ZERO, "0x0000000000000000000000000000000000000000"));
        }
        milestones.put(addr, mViews);
    }

    public void setFunded(String escrowAddress, boolean funded) {
        fundedStates.put(escrowAddress.toLowerCase(), funded);
    }

    public void setCompleted(String escrowAddress, boolean completed) {
        completedStates.put(escrowAddress.toLowerCase(), completed);
    }

    public void setMilestone(String escrowAddress, int index, EscrowMilestoneView view) {
        milestones.computeIfAbsent(escrowAddress.toLowerCase(), k -> new HashMap<>()).put(index, view);
    }

    public void putFactoryEscrow(String factoryAddress, String clientAddress, String projectIdHex, String escrowAddress) {
        String key = (factoryAddress + "-" + clientAddress + "-" + projectIdHex).toLowerCase();
        factoryEscrows.put(key, escrowAddress.toLowerCase());
    }

    @Override
    public long getBlockNumber() {
        return currentBlockNumber;
    }

    @Override
    public BigInteger getChainId() {
        return chainId;
    }

    @Override
    public Optional<TransactionReceipt> getReceipt(String txHash) {
        return Optional.ofNullable(receipts.get(txHash.toLowerCase()));
    }

    @Override
    public int getMilestoneCount(String escrowAddress) {
        return milestoneCounts.getOrDefault(escrowAddress.toLowerCase(), 0);
    }

    @Override
    public EscrowMilestoneView getMilestone(String escrowAddress, int index) {
        Map<Integer, EscrowMilestoneView> m = milestones.get(escrowAddress.toLowerCase());
        if (m == null || !m.containsKey(index)) {
            throw new RuntimeException("Milestone " + index + " not found for escrow " + escrowAddress);
        }
        return m.get(index);
    }

    @Override
    public boolean isFunded(String escrowAddress) {
        return fundedStates.getOrDefault(escrowAddress.toLowerCase(), false);
    }

    @Override
    public boolean isCompleted(String escrowAddress) {
        return completedStates.getOrDefault(escrowAddress.toLowerCase(), false);
    }

    @Override
    public String getClient(String escrowAddress) {
        return clients.getOrDefault(escrowAddress.toLowerCase(), null);
    }

    @Override
    public String getFreelancer(String escrowAddress) {
        return freelancers.getOrDefault(escrowAddress.toLowerCase(), null);
    }

    @Override
    public String getAdmin(String escrowAddress) {
        return admins.getOrDefault(escrowAddress.toLowerCase(), null);
    }

    @Override
    public BigInteger getTotalAmount(String escrowAddress) {
        return totalAmounts.getOrDefault(escrowAddress.toLowerCase(), BigInteger.ZERO);
    }

    @Override
    public String getProjectId(String escrowAddress) {
        return projectIds.getOrDefault(escrowAddress.toLowerCase(), null);
    }

    @Override
    public String getEscrowOf(String factoryAddress, String clientAddress, String projectIdHex) {
        String key = (factoryAddress + "-" + clientAddress + "-" + projectIdHex).toLowerCase();
        return factoryEscrows.getOrDefault(key, "0x0000000000000000000000000000000000000000");
    }

    @Override
    public List<EscrowCreatedEvent> findEscrowCreatedEvents(TransactionReceipt receipt, String factoryAddress) {
        return delegateDecoder.findEscrowCreatedEvents(receipt, factoryAddress);
    }

    @Override
    public List<FundedEvent> findFundedEvents(TransactionReceipt receipt, String escrowAddress) {
        return delegateDecoder.findFundedEvents(receipt, escrowAddress);
    }

    @Override
    public List<MilestoneReleasedEvent> findMilestoneReleasedEvents(TransactionReceipt receipt, String escrowAddress) {
        return delegateDecoder.findMilestoneReleasedEvents(receipt, escrowAddress);
    }

    @Override
    public List<MilestoneSettledEvent> findMilestoneSettledEvents(TransactionReceipt receipt, String escrowAddress) {
        return delegateDecoder.findMilestoneSettledEvents(receipt, escrowAddress);
    }

    @Override
    public List<DisputeOpenedEvent> findDisputeOpenedEvents(TransactionReceipt receipt, String escrowAddress) {
        return delegateDecoder.findDisputeOpenedEvents(receipt, escrowAddress);
    }

    @Override
    public List<DisputeResolvedEvent> findDisputeResolvedEvents(TransactionReceipt receipt, String escrowAddress) {
        return delegateDecoder.findDisputeResolvedEvents(receipt, escrowAddress);
    }
}
