package com.chainlance.chain;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public interface ChainReader {

    long getBlockNumber();

    BigInteger getChainId();

    Optional<TransactionReceipt> getReceipt(String txHash);

    // Escrow views
    int getMilestoneCount(String escrowAddress);

    EscrowMilestoneView getMilestone(String escrowAddress, int index);

    boolean isFunded(String escrowAddress);

    boolean isCompleted(String escrowAddress);

    String getClient(String escrowAddress);

    String getFreelancer(String escrowAddress);

    String getAdmin(String escrowAddress);

    BigInteger getTotalAmount(String escrowAddress);

    String getProjectId(String escrowAddress);

    // Factory views
    String getEscrowOf(String factoryAddress, String clientAddress, String projectIdHex);

    // Event decoders
    List<EscrowCreatedEvent> findEscrowCreatedEvents(TransactionReceipt receipt, String factoryAddress);

    List<FundedEvent> findFundedEvents(TransactionReceipt receipt, String escrowAddress);

    List<MilestoneReleasedEvent> findMilestoneReleasedEvents(TransactionReceipt receipt, String escrowAddress);

    List<MilestoneSettledEvent> findMilestoneSettledEvents(TransactionReceipt receipt, String escrowAddress);

    List<DisputeOpenedEvent> findDisputeOpenedEvents(TransactionReceipt receipt, String escrowAddress);

    List<DisputeResolvedEvent> findDisputeResolvedEvents(TransactionReceipt receipt, String escrowAddress);
}
