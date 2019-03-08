package io.failify.execution;

import io.failify.exceptions.NodeIsNotRunningException;
import io.failify.exceptions.RuntimeEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class NetworkPartitionManager {

    private final static Logger logger = LoggerFactory.getLogger(NetworkPartitionManager.class);
    private final Object synchronizer;

    enum IpTablesCommand {
        APPEND("A"),
        DELETE("D");

        private String option;

        IpTablesCommand(String option) {
            this.option = option;
        }

        public String option() {
            return option;
        }
    }

    private final LimitedRuntimeEngine runtimeEngine;
    private Map<String, Map<String, Integer>> blockedNodesMap;

    public NetworkPartitionManager(LimitedRuntimeEngine runtimeEngine) {
        this.runtimeEngine = runtimeEngine;
        blockedNodesMap = new HashMap<>();
        synchronizer = new Object();

        // initializes blocked nodes for each node
        for (String nodeName: runtimeEngine.nodeNames()) {
            blockedNodesMap.put(nodeName, new HashMap<>());
        }
    }

    private List<Set<String>> generateThePartitionsList(NetPart netPart) {
        List<Set<String>> tempPartitions = new ArrayList<>();
        List<Set<String>> partitions = new ArrayList<>();

        for (String partition: netPart.getPartitions()) {
            tempPartitions.add(Arrays.stream(partition.split(",")).map((s) -> s.trim()).collect(Collectors.toSet()));
        }

        Set<String> restOfNodes = runtimeEngine.nodeNames();
        for (Set<String> partition: tempPartitions) {
            restOfNodes.removeAll(partition);
        }

        // This will put the rest of the nodes which are not listed in the partitions definition at index 0
        partitions.add(restOfNodes);
        partitions.addAll(tempPartitions);

        return partitions;
    }

    public void networkPartition(NetPart netPart) throws RuntimeEngineException {
        logger.info("Applying network partition {} ...", netPart.getPartitionsString());
        synchronized (synchronizer) {
            networkPartitionOpetation(netPart, false);
        }
    }

    public void removeNetworkPartition(NetPart netPart) throws RuntimeEngineException {
        logger.info("Removing network partition {} ...", netPart.getPartitionsString());
        synchronized (synchronizer) {
            networkPartitionOpetation(netPart, true);
        }
    }

    private void networkPartitionOpetation(NetPart netPart, boolean removePartition)
            throws RuntimeEngineException {
        List<Set<String>> partitions = generateThePartitionsList(netPart);

        Map<String, Set<String>> tempBlockedMap = new HashMap<>();

        for (int partNum1 = 0; partNum1 < partitions.size(); partNum1++) {
            for (int partNum2 = 0; partNum2 < partitions.size(); partNum2++) {
                if (partNum1 != partNum2 && !partitions.get(partNum1).isEmpty() &&
                        !partitions.get(partNum2).isEmpty() &&
                        !netPart.getConnections().getOrDefault(partNum1, new HashSet<>()).contains(partNum2)) {
                    for (String node: partitions.get(partNum1)) {
                        for (String blockedNode: partitions.get(partNum2)) {
                            // Calculate what nodes have blocked the blocked node
                            tempBlockedMap.computeIfAbsent(blockedNode, (k) -> new HashSet<>()).add(node);
                        }
                    }
                }
            }
        }


        for (String blockedNode: tempBlockedMap.keySet()) {
            if (removePartition) {
                removeIpTablesRules(blockedNode, tempBlockedMap.get(blockedNode));
            } else {
                addIpTablesRules(blockedNode, tempBlockedMap.get(blockedNode));
            }
        }

    }

    private Set<String> calculateBlockedNodesThatNeedRuleAddition(String host, Set<String> originalList) {
        Set<String> finalList = new HashSet<>();
        for (String node: originalList) {
            if (blockedNodesMap.get(host).getOrDefault(node, 0) == 0) {
                finalList.add(node);
            }
        }

        return finalList;
    }

    private Set<String> calculateBlockedNodesThatNeedRuleRemoval(String host, Set<String> originalList) {
        Set<String> finalList = new HashSet<>();
        for (String node: originalList) {
            if (blockedNodesMap.get(host).getOrDefault(node, 0) - 1 == 0) {
                finalList.add(node);
            }
        }

        return finalList;
    }

    private void addIpTablesRules(String host, Set<String> blockedNodes) throws RuntimeEngineException {
        executeIpTablesBlockCommand(IpTablesCommand.APPEND, host,
                calculateBlockedNodesThatNeedRuleAddition(host, blockedNodes));

        for (String blockedNode : blockedNodes) {
            blockedNodesMap.get(host).put(blockedNode, blockedNodesMap.get(host).getOrDefault(blockedNode, 0) + 1);
        }
    }

    private void removeIpTablesRules(String host, Set<String> blockedNodes) throws RuntimeEngineException {
        if (!blockedNodesMap.get(host).isEmpty()) {
            executeIpTablesBlockCommand(IpTablesCommand.DELETE, host,
                    calculateBlockedNodesThatNeedRuleRemoval(host, blockedNodes));

            for (String blockedNode: blockedNodes) {
                Integer currentBlockCounter = blockedNodesMap.get(host).getOrDefault(blockedNode, 0);

                if (currentBlockCounter == 1) {
                    blockedNodesMap.get(host).remove(blockedNode);
                } else if (currentBlockCounter > 1){
                    blockedNodesMap.get(host).put(blockedNode, currentBlockCounter - 1);
                }
            }
        } else {
            logger.debug("Node {} has no network blockage to be removed!", host);
        }
    }

    // This is useful when start/restarting a node when a network partition is in place
    public void reApplyNetworkPartition(String nodeName) throws RuntimeEngineException {
        if (!blockedNodesMap.get(nodeName).isEmpty()) {
            executeIpTablesBlockCommand(IpTablesCommand.APPEND, nodeName, blockedNodesMap.get(nodeName).keySet());
        }
    }

    private void executeIpTablesBlockCommand(IpTablesCommand command, String host, Set<String> blockedNodes)
            throws RuntimeEngineException {

        if (blockedNodes.isEmpty()) {
            return;
        }

        StringJoiner sources = new StringJoiner(",");

        for (String blockedNode: blockedNodes) {
            sources.add(runtimeEngine.ip(blockedNode));
        }

        String outputCommand = "iptables -" + command.option() + " INPUT -s " + sources.toString() + " -j DROP";
        try {
            CommandResults commandResults = runtimeEngine.runCommandInNode(host, outputCommand);
            if (commandResults.exitCode() != 0) {
                throw new RuntimeEngineException("Error while adding iptables rules to node " + host + "! command: "
                        + outputCommand + " exit code: " + commandResults.exitCode() + " out: "
                        + commandResults.stdOut() + " err: " + commandResults.stdErr());
            }
        } catch (NodeIsNotRunningException e) {
            logger.debug("Cannot apply network blockage rules on node {} because it is not running", host);
        } catch (RuntimeEngineException e) {
            throw new RuntimeEngineException("Error while adding iptables rules to node " + host + "!", e);
        }
    }

}
