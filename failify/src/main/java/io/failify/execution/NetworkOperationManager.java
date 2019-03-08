package io.failify.execution;

import io.failify.exceptions.RuntimeEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class NetworkOperationManager {
    private final static Logger logger = LoggerFactory.getLogger(NetworkOperationManager.class);
    private final Map<String, NetOp.Delay> netDelayMap;
    private final Map<String, NetOp.Loss> netLossMap;
    private final LimitedRuntimeEngine runtimeEngine;
    private final static String IFACE_LIST_COMMAND = "ip -o link | sed 's/[0-9]*: \\([a-z0-9@\\-]*\\):.*/\\1/; s/@.*//;"
            + " /^\\(lo\\|\\)$/d'";

    public NetworkOperationManager(LimitedRuntimeEngine runtimeEngine) {
        netDelayMap = new HashMap<>();
        netLossMap = new HashMap<>();
        this.runtimeEngine = runtimeEngine;
    }

    public void reApplyNetworkOperations(String nodeName) throws RuntimeEngineException {
        NetOp netDelay = netDelayMap.get(nodeName);
        NetOp netLoss = netLossMap.get(nodeName);
        netDelayMap.remove(nodeName);
        netLossMap.remove(nodeName);

        try {
            networkOperation(nodeName, netDelay);
            networkOperation(nodeName, netLoss);
        } catch (RuntimeEngineException e) {
            throw new RuntimeEngineException("Error while re-applying tc rules on node " + nodeName, e);
        }
    }

    public void networkOperation(String nodeName, NetOp netOp) throws RuntimeEngineException {
        if (nodeName == null || netOp == null) return;

        String command = "";
        StringBuilder addCommand = new StringBuilder(IFACE_LIST_COMMAND +
                " | xargs -I % sh -c 'tc qdisc replace dev % root netem ");
        String removeCommand = IFACE_LIST_COMMAND + " | xargs -I % sh -c 'tc qdisc del dev % root";
        if (netOp instanceof NetOp.Delay) {
            if (netLossMap.containsKey(nodeName)) {
                addCommand.append(netOp.getNetemString());
                addCommand.append(" ");
                addCommand.append(netLossMap.get(nodeName).getNetemString());
            } else {
                addCommand.append(netOp.getNetemString());
            }
            command = addCommand.toString();
            netDelayMap.put(nodeName, (NetOp.Delay) netOp);
        } else if (netOp instanceof NetOp.RemoveDelay) {
            if (!netDelayMap.containsKey(nodeName)) return;
            if (netLossMap.containsKey(nodeName)) {
                addCommand.append(netLossMap.get(nodeName).getNetemString());
                command = addCommand.toString();
            } else {
                command = removeCommand;
            }
            netDelayMap.remove(nodeName);
        } else if (netOp instanceof NetOp.Loss) {
            if (netDelayMap.containsKey(nodeName)) {
                addCommand.append(netDelayMap.get(nodeName).getNetemString());
                addCommand.append(" ");
                addCommand.append(netOp.getNetemString());
            } else {
                addCommand.append(netOp.getNetemString());
            }
            command = addCommand.toString();
            netLossMap.put(nodeName, (NetOp.Loss)netOp);
        } else if (netOp instanceof NetOp.RemoveLoss) {
            if (!netLossMap.containsKey(nodeName)) return;
            if (netDelayMap.containsKey(nodeName)) {
                addCommand.append(netDelayMap.get(nodeName).getNetemString());
                command = addCommand.toString();
            } else {
                command = removeCommand;
            }
            netLossMap.remove(nodeName);
        }

        command += "'";

        logger.info("Applying network operation " + netOp);

        CommandResults commandResults = runtimeEngine.runCommandInNode(nodeName, command);
        if (commandResults.exitCode() != 0) {
            throw new RuntimeEngineException("Error while applying tc rules on node " + nodeName + "! command: "
                    + command + " exit code: " + commandResults.exitCode() + " out: "
                    + commandResults.stdOut() + " err: " + commandResults.stdErr());
        }
    }
}
