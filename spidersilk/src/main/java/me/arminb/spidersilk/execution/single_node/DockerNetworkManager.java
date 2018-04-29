package me.arminb.spidersilk.execution.single_node;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Network;
import com.spotify.docker.client.messages.NetworkConfig;
import me.arminb.spidersilk.Constants;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class DockerNetworkManager {
    private final static Logger logger = LoggerFactory.getLogger(DockerNetworkManager.class);

    private final SingleNodeRuntimeEngine runtimeEngine;
    private final DockerClient dockerClient;
    private final String dockerNetworkId;
    private final String dockerNetworkName;
    private final String ipPrefix;
    private Integer currentIp;
    private Map<String, Set<String>> blockedNodesMap;

    public DockerNetworkManager(SingleNodeRuntimeEngine runtimeEngine)
            throws RuntimeEngineException {
        this.runtimeEngine = runtimeEngine;
        this.dockerClient = runtimeEngine.getDockerClient();
        blockedNodesMap = new HashMap<>();

        // Sets docker network's name, creates it and fetches its id
        try {
            dockerNetworkName = Constants.DOCKER_NETWORK_NAME_PREFIX + Instant.now().getEpochSecond();
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

    public String getNewIpAddress() {
        return ipPrefix + currentIp++;
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
                String sources = "";

                for (String otherNode: others) {
                    sources += runtimeEngine.ip(otherNode) + ",";
                }

                sources = sources.substring(0, sources.length() - 1);

                String inputCommand = "iptables -A INPUT -s " + sources + " -j DROP";
                String outputCommand = "iptables -A OUTPUT -s " + sources + " -j DROP";

                for (String partitionNode: partition) {
                    try {
                        if (runtimeEngine.runCommandInNode(partitionNode, inputCommand) != 0) {
                            throw new RuntimeEngineException("Error while adding iptables rules to node " + partitionNode + "!");
                        }
                        if (runtimeEngine.runCommandInNode(partitionNode, outputCommand) != 0) {
                            throw new RuntimeEngineException("Error while adding iptables rules to node " + partitionNode + "!");
                        }
                        blockedNodesMap.get(partitionNode).addAll(others);
                    } catch (InterruptedException e) {
                        throw new RuntimeEngineException("Error while adding iptables rules to node " + partitionNode + "!");
                    } catch (DockerException e) {
                        throw new RuntimeEngineException("Error while adding iptables rules to node " + partitionNode + "!");
                    }
                }
            }
        }
    }

    public void removeNetworkPartition() throws RuntimeEngineException {
        logger.info("Removing network partition (if any) ...");

        for (String nodeName: runtimeEngine.nodeNames()) {
            try {
                if (!blockedNodesMap.get(nodeName).isEmpty()) {
                    String sources = "";

                    for (String blockedNode: blockedNodesMap.get(nodeName)) {
                        sources += runtimeEngine.ip(blockedNode) + ",";
                    }
                    sources = sources.substring(0, sources.length() - 1);

                    String inputCommand = "iptables -D INPUT -s " + sources + " -j DROP";
                    String outputCommand = "iptables -D OUTPUT -s " + sources + " -j DROP";

                    if (runtimeEngine.runCommandInNode(nodeName, inputCommand) != 0) {
                        throw new RuntimeEngineException("Error while trying to delete iptables rules in node " + nodeName + "!");
                    }
                    if (runtimeEngine.runCommandInNode(nodeName, outputCommand) != 0) {
                        throw new RuntimeEngineException("Error while trying to delete iptables rules in node " + nodeName + "!");
                    }
                    blockedNodesMap.put(nodeName, new HashSet<>());
                }
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("Error while trying to delete iptables rules in node " + nodeName + "!");
            } catch (DockerException e) {
                throw new RuntimeEngineException("Error while trying to delete iptables rules in node " + nodeName + "!");
            }
        }
    }

    // This is useful when restarting a node when a network partition is in place
    public void reApplyIptablesRules(String nodeName) throws RuntimeEngineException {
        if (!blockedNodesMap.get(nodeName).isEmpty()) {
            String sources = "";

            for (String blockedNode: blockedNodesMap.get(nodeName)) {
                sources += runtimeEngine.ip(blockedNode) + ",";
            }
            sources = sources.substring(0, sources.length() - 1);

            String inputCommand = "iptables -A INPUT -s " + sources + " -j DROP";
            String outputCommand = "iptables -A OUTPUT -s " + sources + " -j DROP";

            try {
                if (runtimeEngine.runCommandInNode(nodeName, inputCommand) != 0) {
                    throw new RuntimeEngineException("Error while adding iptables rules to node " + nodeName + "!");
                }
                if (runtimeEngine.runCommandInNode(nodeName, outputCommand) != 0) {
                    throw new RuntimeEngineException("Error while adding iptables rules to node " + nodeName + "!");
                }
            } catch (InterruptedException e) {
                throw new RuntimeEngineException("Error while adding iptables rules to node " + nodeName + "!");
            } catch (DockerException e) {
                throw new RuntimeEngineException("Error while adding iptables rules to node " + nodeName + "!");
            }
        }
    }
}
