package io.failify.execution;

import java.util.*;

/**
 * This class is used to define the scheme of node partitions for a network partition
 */
public class NetPart {
    private final String[] partitions; // the partitions of nodes
    private final Map<Integer, Set<Integer>> connections; // the connections between the partitions
    public final static int REST = 0;

    private NetPart(Builder builder) {
        this.partitions = builder.partitions;
        this.connections = Collections.unmodifiableMap(builder.connections);
    }

    public Map<Integer, Set<Integer>> getConnections() {
        return connections;
    }

    public String[] getPartitions() {
        return partitions;
    }

    public String getPartitionsString() {
        StringJoiner stringJoiner = new StringJoiner("-");
        for (String partition: partitions) {
            stringJoiner.add("[" + partition + "]");
        }
        return stringJoiner.toString();
    }

    public static Builder partitions(String... partitions) {
        return new Builder(partitions);
    }

    /**
     * The builder class for building a node partitions object
     */
    public static class Builder {
        private String[] partitions;
        private Map<Integer, Set<Integer>> connections;
        private int maxPartitionNumber = 0;

        /**
         * Constructor
         * @param partitions the node partitions as an array of string where each item is a comma-separated list of
         *                   nodes in a partition. By default, all of the partitions will be disconnected
         */
        public Builder(String... partitions) {
            this.connections = new HashMap<>();
            this.partitions = partitions;
        }

        /**
         * Marks two partitions to be connected in a full-duplex mode
         * @param partition1 the source partition number from the entered list of partitions in the constructor starting
         *                  from 1
         * @param partition2 the destination partition number from the entered list of partitions in the constructor
         *                   starting from 1
         * @return the current builder instance
         */
        public Builder connect(int partition1, int partition2) {
            return connect(partition1, partition2, true);
        }

        /**
         * Marks two partitions to be connected in a full-duplex mode
         * @param partition1 the source partition number from the entered list of partitions in the constructor starting
         *                  from 1
         * @param partition2 the destination partition number from the entered list of partitions in the constructor
         *                   starting from 1
         * @param fullDuplex if true the connection will be full duplex, otherwise the connection will be a simplex from
         *                   source to destination
         * @return the current builder instance
         */
        public Builder connect(int partition1, int partition2, boolean fullDuplex) {

            if (partition1 == partition2) {
                throw new RuntimeException("Partition numbers cannot be equal!");
            }

            if ((partition1 != 0 && partition1 < 1) || (partition2 != 0 && partition2 < 1)) {
                throw new RuntimeException("Partition numbers cannot be lower than 1");
            }

            if (Math.max(partition1, partition2) > maxPartitionNumber) {
                maxPartitionNumber = Math.max(partition1, partition2);
            }

            connections.computeIfAbsent(partition1, k -> new HashSet<>()).add(partition2);
            if (fullDuplex) {
                connections.computeIfAbsent(partition2, k -> new HashSet<>()).add(partition1);
            } else {
                // if a simplex connection is being defined for a previously defined full duplex connection
                connections.getOrDefault(partition2, new HashSet<>()).remove(partition1);
            }

            return this;
        }

        /**
         * @return the built node partitions object based on the given configuration
         */
        public NetPart build() {
            if (partitions == null) {
                throw new RuntimeException("Partitions cannot be null");
            }

            Set<String> nodes = new HashSet<>();

            for (String partition: partitions) {
                if (partition == null || partition.isEmpty()) {
                    throw new RuntimeException("Partitions cannot be empty");
                }
                for (String node: partition.split(",")) {
                    node = node.trim();
                    if (nodes.contains(node)) {
                        throw new RuntimeException("Node " + node + " has appeared multiple times either in the same or "
                                + "different partitions.");
                    }
                }
            }

            if (maxPartitionNumber > partitions.length) {
                throw new RuntimeException("The numbers number used in connecting the partitions cannot be larger than "
                        + "the actual number of partitions");
            }

            return new NetPart(this);
        }
    }
}
