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

package io.failify.execution;

import io.failify.dsl.entities.PortType;
import io.failify.exceptions.NodeIsNotRunningException;
import io.failify.exceptions.NodeNotFoundException;
import io.failify.exceptions.RuntimeEngineException;

import java.util.Set;
import java.util.concurrent.TimeoutException;

public interface LimitedRuntimeEngine {
    // Runtime Operation

    /**
     * Kills a node in the deployed environment. It won't throw an exception if the node is not running
     * @param nodeName the node name to be killed
     * @throws RuntimeEngineException is something goes wrong
     * @throws NodeNotFoundException if the node doesn't exist
     */
    void killNode(String nodeName) throws RuntimeEngineException;

    /**
     * Stops a node in the deployed environment. It won't throw an exception if the node is not running
     * @param nodeName the node name to be Stopped
     * @param secondsUntilForcedStop the number of seconds to wait until forcing a stop
     * @throws RuntimeEngineException is something goes wrong
     * @throws NodeNotFoundException if the node doesn't exist
     */
    void stopNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException;

    /**
     * Starts a node in the deployed environment. It won't throw an exception if the node is already started
     * @param nodeName the node name to be started
     * @throws RuntimeEngineException is something goes wrong
     * @throws NodeNotFoundException if the node doesn't exist
     */
    void startNode(String nodeName) throws RuntimeEngineException;

    /**
     * Restarts a node in the deployed environment. It won't throw an exception if the node is not running
     * @param nodeName the node name to be restarted
     * @param secondsUntilForcedStop the number of seconds to wait until forcing a stop
     * @throws RuntimeEngineException is something goes wrong
     * @throws NodeNotFoundException if the node doesn't exist
     */
    void restartNode(String nodeName, Integer secondsUntilForcedStop) throws RuntimeEngineException;

    /**
     * Applies a clock drift with the given amount on the given node name
     * @param nodeName the node name to apply the clock drift on
     * @param amount the positive or negative amount of time offset to apply in milliseconds
     * @throws RuntimeEngineException is something goes wrong
     * @throws NodeNotFoundException if the node doesn't exist
     */
    void clockDrift(String nodeName, Integer amount) throws RuntimeEngineException;

    /**
     * Imposes a network partition based on the given partition scheme in the deployed environment
     * @param netPart the desired scheme for the partition. Take a look at NetPart class for more information
     * @throws RuntimeEngineException if something goes wrong
     * @throws NodeNotFoundException if one of partitions includes a node that doesn't exist
     */
    void networkPartition(NetPart netPart) throws RuntimeEngineException;

    /**
     * Removes a network partition based on the given partition scheme in the deployed environment
     * @param netPart the desired scheme for the partition. Take a look at NetPart class for more information
     * @throws RuntimeEngineException if something goes wrong
     * @throws NodeNotFoundException if one of partitions includes a node that doesn't exist
     */
    void removeNetworkPartition(NetPart netPart) throws RuntimeEngineException;

    /**
     * Executes a shell command in the given node
     * @param nodeName the node name to execute the shell command into
     * @param command the command to be executed
     * @return the command execution results including exit code, stdout and stderr
     * @throws RuntimeEngineException if something goes wrong
     * @throws NodeIsNotRunningException if the node is not running
     * @throws NodeNotFoundException if the node doesn't exist
     */
    CommandResults runCommandInNode(String nodeName, String command) throws RuntimeEngineException;

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
    void enforceOrder(String eventName, FailifyCheckedRunnable action) throws RuntimeEngineException;

    /**
     * This method waits for the given workload event's dependencies to be marked as satisfied in the event server,
     * executes the given action, and finally, marks the given event name as satisfied in the event server. The wait
     * timeouts after the given timeout amount in seconds
     * @param eventName the event name to wait for
     * @param timeout the timeout amount in seconds
     * @param action the action to execute after waiting is completed
     * @throws RuntimeEngineException if something goes wrong or the event name is not referred to in the run sequence
     * or the event is not a workload event
     * @throws TimeoutException if the wait timeouts
     */
    void enforceOrder(String eventName, Integer timeout, FailifyCheckedRunnable action)
            throws RuntimeEngineException, TimeoutException;
}
