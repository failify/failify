/*
 * MIT License
 *
 * Copyright (c) 2017-2019 Armin Balalaie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package me.arminb.spidersilk.execution;

import me.arminb.spidersilk.dsl.entities.PortType;
import me.arminb.spidersilk.exceptions.RuntimeEngineException;

import java.util.Set;
import java.util.concurrent.TimeoutException;

public interface LimitedRuntimeEngine {
    // Runtime Operation

    /**
     * Kills a node in the deployed environment
     * @param nodeName the node name to be killed
     * @throws RuntimeEngineException is something goes wrong
     */
    void killNode(String nodeName) throws RuntimeEngineException;

    /**
     * Stops a node in the deployed environment
     * @param nodeName the node name to be Stopped
     * @throws RuntimeEngineException is something goes wrong
     */
    void stopNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException;

    /**
     * Starts a node in the deployed environment
     * @param nodeName the node name to be started
     * @throws RuntimeEngineException is something goes wrong
     */
    void startNode(String nodeName) throws RuntimeEngineException;

    /**
     * Restarts a node in the deployed environment
     * @param nodeName the node name to be restarted
     * @throws RuntimeEngineException is something goes wrong
     */
    void restartNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException;

    /**
     * Applies a clock drift with the given amount on the given node name
     * @param nodeName the node name to apply the clock drift on
     * @param amount the positive or negative amount of time offset to apply in milliseconds
     * @throws RuntimeEngineException is something goes wrong
     */
    void clockDrift(String nodeName, Integer amount) throws RuntimeEngineException;

    /**
     * Disconnects the network connection between two nodes in the deployed environment
     * @param node1 the first node name
     * @param node2 the second node name
     * @throws RuntimeEngineException if something goes wrong
     */
    void linkDown(String node1, String node2) throws RuntimeEngineException;

    /**
     * Connects the disconnected network connection between two nodes in the deployed environment
     * @param node1 the first node name
     * @param node2 the second node name
     * @throws RuntimeEngineException if something goes wrong
     */
    void linkUp(String node1, String node2) throws RuntimeEngineException;

    /**
     * Imposes a network partition based on the given partition scheme in the deployed environment
     * @param nodePartitions the desired scheme for the partition. Nodes should be separated with dash(-) and partitions
     *                       should be separated with comma. "n1-n2,n3" means a network partition with n1 and n2 in one
     *                       side and n3 at the other side. More than two partitions is also possible. For example
     *                       "n1,n2,n3". Also, if all the nodes are not included in the string, the rest of nodes would
     *                       be considered as another partition.
     * @throws RuntimeEngineException if something goes wrong
     */
    void networkPartition(String nodePartitions) throws RuntimeEngineException;

    /**
     * Removes all of the imposed network blockings in the deployed environment
     * @throws RuntimeEngineException if something goes wrong
     */
    void removeNetworkPartition() throws RuntimeEngineException;

    // TODO Change int to ProcessResults
    /**
     * Executes a shell command in the given node
     * @param nodeName the node name to execute the shell command into
     * @param command the command to be executed
     * @return the exit code of shell command's process
     * @throws RuntimeEngineException if something goes wrong
     */
    long runCommandInNode(String nodeName, String command) throws RuntimeEngineException;

    // Runtime Info

    /**
     * @return a set of the deployed node names
     */
    Set<String> nodeNames();

    /**
     * @param nodeName the node name to find ip address for
     * @return the ip address of the given node name or null if the node is not found
     */
    String ip(String nodeName);

    /**
     * Returns the port mapping for a udp/tcp port number in a specific node
     * @param nodeName the node name
     * @param portNumber the  source port number
     * @param portType tcp or udp
     * @return the mapped port number or null if either the node or the desired port mapping is not found
     */
    Integer portMapping(String nodeName, Integer portNumber, PortType portType);

    // Events

    /**
     * This method waits indefinitely for the given event's dependencies to be marked as satisfied in the event server.
     * @param eventName the event name to wait for
     * @throws RuntimeEngineException if something goes wrong or the event name is not referred to in the run sequence
     */
    void waitFor(String eventName) throws RuntimeEngineException;

    /**
     * This method waits indefinitely for the given event's dependencies and the event itself (if desired) to be marked
     * as satisfied in the event server.
     * @param eventName the event name to wait for
     * @param includeEvent if the wait should include the event itself or not
     * @throws RuntimeEngineException if something goes wrong or the event name is not referred to in the run sequence
     */
    void waitFor(String eventName, Boolean includeEvent) throws RuntimeEngineException;

    /**
     * This method waits for the given event's dependencies to be marked as satisfied in the event server. The wait
     * timeouts after the given timeout amount in seconds
     * @param eventName the event name to wait for
     * @param timeout the timeout amount in seconds
     * @throws RuntimeEngineException if something goes wrong or the event name is not referred to in the run sequence
     * @throws TimeoutException if the wait timeouts
     */
    void waitFor(String eventName, Integer timeout) throws RuntimeEngineException, TimeoutException;

    /**
     * This method waits for the given event's dependencies and the event itself (if desired) to be marked as satisfied in
     * the event server. The wait timeouts after the given timeout amount in seconds
     * @param eventName the event name to wait for
     * @param includeEvent if the wait should include the event itself or not
     * @param timeout the timeout amount in seconds
     * @throws RuntimeEngineException if something goes wrong or the event name is not referred to in the run sequence
     * @throws TimeoutException if the wait timeouts
     */
    void waitFor(String eventName, Boolean includeEvent, Integer timeout) throws RuntimeEngineException, TimeoutException;

    /**
     * This method waits indefinitely for the given workload event's dependencies to be marked as satisfied in the event server,
     * executes the given action, and finally, marks the given event name as satisfied in the event server.
     * @param eventName the event name to wait for
     * @param action the action to execute after waiting is completed
     * @throws RuntimeEngineException if something goes wrong or the event name is not referred to in the run sequence
     * or the event is not a workload event
     */
    void enforceOrder(String eventName, SpiderSilkCheckedRunnable action) throws RuntimeEngineException;

    /**
     * This method waits for the given workload event's dependencies to be marked as satisfied in the event server,
     * executes the given action, and finally, marks the given event name as satisfied in the event server. The wait
     * timeouts after the given timeout amount in seconds
     * @param eventName the event name to wait for
     * @param action the action to execute after waiting is completed
     * @param timeout the timeout amount in seconds
     * @throws RuntimeEngineException if something goes wrong or the event name is not referred to in the run sequence
     * or the event is not a workload event
     * @throws TimeoutException if the wait timeouts
     */
    void enforceOrder(String eventName, SpiderSilkCheckedRunnable action, Integer timeout)
            throws RuntimeEngineException, TimeoutException;
}
