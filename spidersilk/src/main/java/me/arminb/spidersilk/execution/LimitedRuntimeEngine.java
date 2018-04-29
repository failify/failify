package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.exceptions.RuntimeEngineException;

import java.util.Set;

public interface LimitedRuntimeEngine {
    // Runtime Operation
    void killNode(String nodeName) throws RuntimeEngineException;
    void stopNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException;
    void startNode(String nodeName) throws RuntimeEngineException;
    void restartNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException;
    void clockDrift(String nodeName) throws RuntimeEngineException;
    void networkPartition(String nodePartitions) throws RuntimeEngineException;
    void removeNetworkPartition() throws RuntimeEngineException;

    // Runtime Info
    Set<String> nodeNames();
    String ip(String nodeName);

    // Events
    void waitFor(String eventName);
    void sendEvent(String eventName);
}
