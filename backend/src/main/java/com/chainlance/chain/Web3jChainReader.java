package com.chainlance.chain;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

@Component
public class Web3jChainReader implements ChainReader {

    private final Web3j web3j;

    // Events
    private static final Event ESCROW_CREATED_EVENT = new Event(
            "EscrowCreated",
            Arrays.asList(
                    new TypeReference<Bytes32>(true) {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Address>(false) {},
                    new TypeReference<Address>(false) {},
                    new TypeReference<Uint256>(false) {}
            )
    );
    private static final String ESCROW_CREATED_TOPIC = EventEncoder.encode(ESCROW_CREATED_EVENT);

    private static final Event FUNDED_EVENT = new Event(
            "Funded",
            Arrays.asList(
                    new TypeReference<Address>(true) {},
                    new TypeReference<Uint256>(false) {}
            )
    );
    private static final String FUNDED_TOPIC = EventEncoder.encode(FUNDED_EVENT);

    private static final Event MILESTONE_RELEASED_EVENT = new Event(
            "MilestoneReleased",
            Arrays.asList(
                    new TypeReference<Uint256>(true) {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Uint256>(false) {}
            )
    );
    private static final String MILESTONE_RELEASED_TOPIC = EventEncoder.encode(MILESTONE_RELEASED_EVENT);

    private static final Event MILESTONE_SETTLED_EVENT = new Event(
            "MilestoneSettled",
            Arrays.asList(
                    new TypeReference<Uint256>(true) {},
                    new TypeReference<Uint256>(false) {},
                    new TypeReference<Uint256>(false) {}
            )
    );
    private static final String MILESTONE_SETTLED_TOPIC = EventEncoder.encode(MILESTONE_SETTLED_EVENT);

    private static final Event DISPUTE_OPENED_EVENT = new Event(
            "DisputeOpened",
            Arrays.asList(
                    new TypeReference<Uint256>(true) {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Bytes32>(false) {}
            )
    );
    private static final String DISPUTE_OPENED_TOPIC = EventEncoder.encode(DISPUTE_OPENED_EVENT);

    private static final Event DISPUTE_RESOLVED_EVENT = new Event(
            "DisputeResolved",
            Arrays.asList(
                    new TypeReference<Uint256>(true) {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Uint256>(false) {},
                    new TypeReference<Uint256>(false) {},
                    new TypeReference<Bytes32>(false) {}
            )
    );
    private static final String DISPUTE_RESOLVED_TOPIC = EventEncoder.encode(DISPUTE_RESOLVED_EVENT);

    public Web3jChainReader(Web3j web3j) {
        this.web3j = web3j;
    }

    @Override
    public long getBlockNumber() {
        try {
            EthBlockNumber res = web3j.ethBlockNumber().send();
            if (res.hasError()) {
                throw new RuntimeException("RPC error fetching block number: " + res.getError().getMessage());
            }
            return res.getBlockNumber().longValue();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read block number from RPC: " + e.getMessage(), e);
        }
    }

    @Override
    public BigInteger getChainId() {
        try {
            EthChainId res = web3j.ethChainId().send();
            if (res.hasError()) {
                throw new RuntimeException("RPC error fetching chain id: " + res.getError().getMessage());
            }
            return res.getChainId();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read chain id from RPC: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<TransactionReceipt> getReceipt(String txHash) {
        try {
            EthGetTransactionReceipt res = web3j.ethGetTransactionReceipt(txHash).send();
            if (res.hasError()) {
                throw new RuntimeException("RPC error fetching receipt: " + res.getError().getMessage());
            }
            return res.getTransactionReceipt();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read transaction receipt from RPC: " + e.getMessage(), e);
        }
    }

    @Override
    public int getMilestoneCount(String escrowAddress) {
        Function fn = new Function("milestoneCount", Collections.emptyList(), List.of(new TypeReference<Uint256>() {}));
        List<Type> result = call(escrowAddress, fn);
        return ((BigInteger) result.get(0).getValue()).intValue();
    }

    @Override
    public EscrowMilestoneView getMilestone(String escrowAddress, int index) {
        Function fn = new Function(
                "getMilestone",
                List.of(new Uint256(BigInteger.valueOf(index))),
                Arrays.asList(
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint8>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Address>() {}
                )
        );
        List<Type> result = call(escrowAddress, fn);
        BigInteger allocation = (BigInteger) result.get(0).getValue();
        int status = ((BigInteger) result.get(1).getValue()).intValue();
        BigInteger freelancerPaid = (BigInteger) result.get(2).getValue();
        BigInteger clientRefunded = (BigInteger) result.get(3).getValue();
        String disputedBy = ((String) result.get(4).getValue()).toLowerCase();
        return new EscrowMilestoneView(allocation, status, freelancerPaid, clientRefunded, disputedBy);
    }

    @Override
    public boolean isFunded(String escrowAddress) {
        Function fn = new Function("funded", Collections.emptyList(), List.of(new TypeReference<Bool>() {}));
        List<Type> result = call(escrowAddress, fn);
        return (Boolean) result.get(0).getValue();
    }

    @Override
    public boolean isCompleted(String escrowAddress) {
        Function fn = new Function("isCompleted", Collections.emptyList(), List.of(new TypeReference<Bool>() {}));
        List<Type> result = call(escrowAddress, fn);
        return (Boolean) result.get(0).getValue();
    }

    @Override
    public String getClient(String escrowAddress) {
        Function fn = new Function("client", Collections.emptyList(), List.of(new TypeReference<Address>() {}));
        List<Type> result = call(escrowAddress, fn);
        return ((String) result.get(0).getValue()).toLowerCase();
    }

    @Override
    public String getFreelancer(String escrowAddress) {
        Function fn = new Function("freelancer", Collections.emptyList(), List.of(new TypeReference<Address>() {}));
        List<Type> result = call(escrowAddress, fn);
        return ((String) result.get(0).getValue()).toLowerCase();
    }

    @Override
    public String getAdmin(String escrowAddress) {
        Function fn = new Function("admin", Collections.emptyList(), List.of(new TypeReference<Address>() {}));
        List<Type> result = call(escrowAddress, fn);
        return ((String) result.get(0).getValue()).toLowerCase();
    }

    @Override
    public BigInteger getTotalAmount(String escrowAddress) {
        Function fn = new Function("totalAmount", Collections.emptyList(), List.of(new TypeReference<Uint256>() {}));
        List<Type> result = call(escrowAddress, fn);
        return (BigInteger) result.get(0).getValue();
    }

    @Override
    public String getProjectId(String escrowAddress) {
        Function fn = new Function("projectId", Collections.emptyList(), List.of(new TypeReference<Bytes32>() {}));
        List<Type> result = call(escrowAddress, fn);
        return Numeric.toHexString((byte[]) result.get(0).getValue());
    }

    @Override
    public String getEscrowOf(String factoryAddress, String clientAddress, String projectIdHex) {
        Function fn = new Function(
                "escrowOf",
                Arrays.asList(
                        new Address(clientAddress),
                        new Bytes32(Numeric.hexStringToByteArray(projectIdHex))
                ),
                List.of(new TypeReference<Address>() {})
        );
        List<Type> result = call(factoryAddress, fn);
        return ((String) result.get(0).getValue()).toLowerCase();
    }

    // ------------------------------------------------------------- Event decoders

    @Override
    public List<EscrowCreatedEvent> findEscrowCreatedEvents(TransactionReceipt receipt, String factoryAddress) {
        List<EscrowCreatedEvent> list = new ArrayList<>();
        if (receipt.getLogs() == null) return list;

        for (Log log : receipt.getLogs()) {
            if (factoryAddress != null && !log.getAddress().equalsIgnoreCase(factoryAddress)) {
                continue;
            }
            if (log.getTopics() == null || log.getTopics().isEmpty()) {
                continue;
            }
            if (!log.getTopics().get(0).equalsIgnoreCase(ESCROW_CREATED_TOPIC)) {
                continue;
            }
            if (log.getTopics().size() < 4) {
                continue;
            }

            String projectId = log.getTopics().get(1);
            String escrow = parseAddressTopic(log.getTopics().get(2));
            String client = parseAddressTopic(log.getTopics().get(3));

            List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(), ESCROW_CREATED_EVENT.getNonIndexedParameters());
            if (nonIndexed.size() < 3) {
                continue;
            }
            String freelancer = ((String) nonIndexed.get(0).getValue()).toLowerCase();
            String admin = ((String) nonIndexed.get(1).getValue()).toLowerCase();
            BigInteger totalAmount = (BigInteger) nonIndexed.get(2).getValue();

            list.add(new EscrowCreatedEvent(projectId, escrow, client, freelancer, admin, totalAmount));
        }
        return list;
    }

    @Override
    public List<FundedEvent> findFundedEvents(TransactionReceipt receipt, String escrowAddress) {
        List<FundedEvent> list = new ArrayList<>();
        if (receipt.getLogs() == null) return list;

        for (Log log : receipt.getLogs()) {
            if (escrowAddress != null && !log.getAddress().equalsIgnoreCase(escrowAddress)) {
                continue;
            }
            if (log.getTopics() == null || log.getTopics().isEmpty()) {
                continue;
            }
            if (!log.getTopics().get(0).equalsIgnoreCase(FUNDED_TOPIC)) {
                continue;
            }
            if (log.getTopics().size() < 2) {
                continue;
            }

            String client = parseAddressTopic(log.getTopics().get(1));
            List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(), FUNDED_EVENT.getNonIndexedParameters());
            if (nonIndexed.isEmpty()) continue;
            BigInteger amount = (BigInteger) nonIndexed.get(0).getValue();

            list.add(new FundedEvent(client, amount));
        }
        return list;
    }

    @Override
    public List<MilestoneReleasedEvent> findMilestoneReleasedEvents(TransactionReceipt receipt, String escrowAddress) {
        List<MilestoneReleasedEvent> list = new ArrayList<>();
        if (receipt.getLogs() == null) return list;

        for (Log log : receipt.getLogs()) {
            if (escrowAddress != null && !log.getAddress().equalsIgnoreCase(escrowAddress)) {
                continue;
            }
            if (log.getTopics() == null || log.getTopics().isEmpty()) {
                continue;
            }
            if (!log.getTopics().get(0).equalsIgnoreCase(MILESTONE_RELEASED_TOPIC)) {
                continue;
            }
            if (log.getTopics().size() < 3) {
                continue;
            }

            int index = Numeric.toBigInt(log.getTopics().get(1)).intValue();
            String freelancer = parseAddressTopic(log.getTopics().get(2));
            List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(), MILESTONE_RELEASED_EVENT.getNonIndexedParameters());
            if (nonIndexed.isEmpty()) continue;
            BigInteger amount = (BigInteger) nonIndexed.get(0).getValue();

            list.add(new MilestoneReleasedEvent(index, freelancer, amount));
        }
        return list;
    }

    @Override
    public List<MilestoneSettledEvent> findMilestoneSettledEvents(TransactionReceipt receipt, String escrowAddress) {
        List<MilestoneSettledEvent> list = new ArrayList<>();
        if (receipt.getLogs() == null) return list;

        for (Log log : receipt.getLogs()) {
            if (escrowAddress != null && !log.getAddress().equalsIgnoreCase(escrowAddress)) {
                continue;
            }
            if (log.getTopics() == null || log.getTopics().isEmpty()) {
                continue;
            }
            if (!log.getTopics().get(0).equalsIgnoreCase(MILESTONE_SETTLED_TOPIC)) {
                continue;
            }
            if (log.getTopics().size() < 2) {
                continue;
            }

            int index = Numeric.toBigInt(log.getTopics().get(1)).intValue();
            List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(), MILESTONE_SETTLED_EVENT.getNonIndexedParameters());
            if (nonIndexed.size() < 2) continue;
            BigInteger fAmount = (BigInteger) nonIndexed.get(0).getValue();
            BigInteger cAmount = (BigInteger) nonIndexed.get(1).getValue();

            list.add(new MilestoneSettledEvent(index, fAmount, cAmount));
        }
        return list;
    }

    @Override
    public List<DisputeOpenedEvent> findDisputeOpenedEvents(TransactionReceipt receipt, String escrowAddress) {
        List<DisputeOpenedEvent> list = new ArrayList<>();
        if (receipt.getLogs() == null) return list;

        for (Log log : receipt.getLogs()) {
            if (escrowAddress != null && !log.getAddress().equalsIgnoreCase(escrowAddress)) {
                continue;
            }
            if (log.getTopics() == null || log.getTopics().isEmpty()) {
                continue;
            }
            if (!log.getTopics().get(0).equalsIgnoreCase(DISPUTE_OPENED_TOPIC)) {
                continue;
            }
            if (log.getTopics().size() < 3) {
                continue;
            }

            int index = Numeric.toBigInt(log.getTopics().get(1)).intValue();
            String openedBy = parseAddressTopic(log.getTopics().get(2));
            List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(), DISPUTE_OPENED_EVENT.getNonIndexedParameters());
            if (nonIndexed.isEmpty()) continue;
            String reasonHash = Numeric.toHexString((byte[]) nonIndexed.get(0).getValue());

            list.add(new DisputeOpenedEvent(index, openedBy, reasonHash));
        }
        return list;
    }

    @Override
    public List<DisputeResolvedEvent> findDisputeResolvedEvents(TransactionReceipt receipt, String escrowAddress) {
        List<DisputeResolvedEvent> list = new ArrayList<>();
        if (receipt.getLogs() == null) return list;

        for (Log log : receipt.getLogs()) {
            if (escrowAddress != null && !log.getAddress().equalsIgnoreCase(escrowAddress)) {
                continue;
            }
            if (log.getTopics() == null || log.getTopics().isEmpty()) {
                continue;
            }
            if (!log.getTopics().get(0).equalsIgnoreCase(DISPUTE_RESOLVED_TOPIC)) {
                continue;
            }
            if (log.getTopics().size() < 3) {
                continue;
            }

            int index = Numeric.toBigInt(log.getTopics().get(1)).intValue();
            String admin = parseAddressTopic(log.getTopics().get(2));
            List<Type> nonIndexed = FunctionReturnDecoder.decode(log.getData(), DISPUTE_RESOLVED_EVENT.getNonIndexedParameters());
            if (nonIndexed.size() < 3) continue;
            BigInteger fAmount = (BigInteger) nonIndexed.get(0).getValue();
            BigInteger cAmount = (BigInteger) nonIndexed.get(1).getValue();
            String resolutionHash = Numeric.toHexString((byte[]) nonIndexed.get(2).getValue());

            list.add(new DisputeResolvedEvent(index, admin, fAmount, cAmount, resolutionHash));
        }
        return list;
    }

    // ------------------------------------------------------------- helpers

    private List<Type> call(String to, Function function) {
        String encoded = FunctionEncoder.encode(function);
        Transaction tx = Transaction.createEthCallTransaction(null, to, encoded);
        try {
            EthCall res = web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send();
            if (res.hasError()) {
                throw new RuntimeException("eth_call reverted or returned error: " + res.getError().getMessage());
            }
            String value = res.getValue();
            if (value == null || value.equals("0x") || value.isEmpty()) {
                throw new RuntimeException("eth_call returned empty value (contract may not exist at address " + to + ")");
            }
            return FunctionReturnDecoder.decode(value, function.getOutputParameters());
        } catch (IOException e) {
            throw new RuntimeException("RPC error calling " + function.getName() + ": " + e.getMessage(), e);
        }
    }

    private static String parseAddressTopic(String topic) {
        if (topic == null || topic.length() < 40) {
            return topic;
        }
        return "0x" + topic.substring(topic.length() - 40).toLowerCase();
    }
}
