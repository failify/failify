package me.arminb.spidersilk.execution.single_node;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Network;
import com.spotify.docker.client.messages.NetworkConfig;
import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import me.arminb.spidersilk.execution.LimitedRuntimeEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class DockerNetworkManager {
    private final static Logger logger = LoggerFactory.getLogger(DockerNetworkManager.class);

    private final LimitedRuntimeEngine runtimeEngine;
    private final DockerClient dockerClient;
    private final String dockerNetworkId;
    private final String dockerNetworkName;
    private final String ipPrefix;
    private final String hostIp;
    private Integer currentIp;
    private Map<String, Set<String>> blockedNodesMap;

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

    public DockerNetworkManager(String deploymentName, LimitedRuntimeEngine runtimeEngine, DockerClient dockerClient)
            throws RuntimeEngineException {
        this.runtimeEngine = runtimeEngine;
        this.dockerClient = dockerClient;
        blockedNodesMap = new HashMap<>();

        // Sets docker network's name, creates it and fetches its id
        try {
            dockerNetworkName = Constants.DOCKER_NETWORK_NAME_PREFIX + deploymentName + "_" + Instant.now().getEpochSecond();
            dockerNetworkId = dockerClient.createNetwork(NetworkConfig.builder()
                    .name(dockerNetworkName)
                    .build()).id();
            logger.info("Docker network {} is created!", dockerNetworkId);
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error in creating docker network!");
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error in creating docker network!");
        }

        // Calculates ipPrefix and currentIp for future ip assignments
        try {
            String gateway = inspectDockerNetwork().ipam().config().get(0).gateway();
            hostIp = gateway;
            ipPrefix = gateway.substring(0, gateway.lastIndexOf(".") + 1);
            currentIp = Integer.parseInt(gateway.substring(gateway.lastIndexOf(".") + 1, gateway.length())) + 1;
        } catch (RuntimeEngineException e) {
            deleteDockerNetwork();
            throw e;
        }

        // initializes blocked nodes for each node
        for (String nodeName: runtimeEngine.nodeNames()) {
            blockedNodesMap.put(nodeName, new HashSet<>());
        }
    }

    public String dockerNetworkId() {
        return dockerNetworkId;
    }

    public String dockerNetworkName() {
        return dockerNetworkName;
    }

    public void deleteDockerNetwork() throws RuntimeEngineException {
        try {
            if (dockerNetworkId != null) {
                dockerClient.removeNetwork(dockerNetworkId);
            }
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error in deleting docker network" + dockerNetworkId + "!");
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error in deleting docker network" + dockerNetworkId + "!");
        }
    }

    private Network inspectDockerNetwork() throws RuntimeEngineException {
        try {
            Network network = dockerClient.inspectNetwork(dockerNetworkId);
            return network;
        } catch (DockerException e) {
            throw new RuntimeEngineException("Error while trying to inspect docker network " + dockerNetworkId + "!");
        } catch (InterruptedException e) {
            throw new RuntimeEngineException("Error while trying to inspect docker network " + dockerNetworkId + "!");
        }
    }

    public String getHostIpAddress() {
        return hostIp;
    }

    public String getNewIpAddress() {
        return ipPrefix + currentIp++;
    }

    // TODO The rest of this class can be moved to another class to be re-used by multi node engine

    void linkDown(String node1, String node2) throws RuntimeEngineException {
        Set<String> blockedNodes = new HashSet<>();

        blockedNodes.add(node2);
        addIpTablesRules(node1, blockedNodes);

        blockedNodes.clear();
        blockedNodes.add(node1);
        addIpTablesRules(node2, blockedNodes);
    }

    void linkUp(String node1, String node2) throws RuntimeEngineException {
        Set<String> blockedNodes = new HashSet<>();

        blockedNodes.add(node2);
        removeIpTablesRules(node1, blockedNodes);

        blockedNodes.clear();
        blockedNodes.add(node1);
        removeIpTablesRules(node2, blockedNodes);
    }

    public void networkPartition(String nodePartitions) throws RuntimeEngineException {
        removeNetworkPartition();
        logger.info("Applying network partition {} ...", nodePartitions);
        List<Set<String>> partitions = new ArrayList<>();

        for (String partition: nodePartitions.trim().split(",")) {
            partitions.add(new HashSet<String>(Arrays.asList(partition.trim().split("-"))));
        }

        for (Set<String> partition: partitions) {
            Set<String> others = new HashSet<>(runtimeEngine.nodeNames());
            others.removeAll(partition);

            if (!others.isEmpty()) {
                for (String partitionNode: partition) {
                    addIpTablesRules(partitionNode, others);
                }
            }
        }
    }

    public void removeNetworkPartition() throws RuntimeEngineException {
        logger.info("Removing network partition (if any) ...");

        for (String nodeName: runtimeEngine.nodeNames()) {
            try {
                removeIpTablesRules(nodeName, blockedNodesMap.get(nodeName));
            } catch (RuntimeEngineException e) {
                throw new RuntimeEngineException("Error while trying to delete iptables rules in node " + nodeName + "!");
            }
        }
    }

    // This is useful when restarting a node when a network partition is in place
    public void reApplyIptablesRules(String nodeName) throws RuntimeEngineException {
        if (!blockedNodesMap.get(nodeName).isEmpty()) {
            addIpTablesRules(nodeName, blockedNodesMap.get(nodeName));
        }
    }

    private void addIpTablesRules(String host, Set<String> blockedNodes) throws RuntimeEngineException {
        executeIpTablesBlockCommand(IpTablesCommand.APPEND, host, blockedNodes);
        blockedNodesMap.get(host).addAll(blockedNodes);

    }

    // TODO It is possible that removePartition removes linkDown rules if they any overlaps
    private void removeIpTablesRules(String host, Set<String> blockedNodes) throws RuntimeEngineException {
        if (!blockedNodesMap.get(host).isEmpty()) {
            executeIpTablesBlockCommand(IpTablesCommand.DELETE, host, blockedNodes);
            blockedNodesMap.get(host).removeAll(blockedNodes);
        } else {
            logger.warn("Node {} has no network blockage to be removed!", host);
        }
    }

    private void executeIpTablesBlockCommand(IpTablesCommand command, String host, Set<String> blockedNodes) throws RuntimeEngineException {
        StringJoiner sources = new StringJoiner(",");

        for (String otherNode: blockedNodes) {
            sources.add(runtimeEngine.ip(otherNode));
        }

        String inputCommand = "iptables -" + command.option() + " INPUT -s " + sources.toString() + " -j DROP";
        String outputCommand = "iptables -" + command.option() + " OUTPUT -s " + sources.toString() + " -j DROP";

        try {

            if (runtimeEngine.runCommandInNode(host, inputCommand) != 0) {
                throw new RuntimeEngineException("Error while adding iptables rules to node " + host + "!");
            }
            if (runtimeEngine.runCommandInNode(host, outputCommand) != 0) {
                throw new RuntimeEngineException("Error while adding iptables rules to node " + host + "!");
            }
        } catch (RuntimeEngineException e) {
            throw new RuntimeEngineException("Error while adding iptables rules to node " + host + "!");
        }
    }
}
