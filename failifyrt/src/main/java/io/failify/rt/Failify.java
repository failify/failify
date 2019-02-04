/*
 * MIT License
 *
 * Copyright (c) 2017 Armin Balalaie
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

package io.failify.rt;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeoutException;

// TODO should some methods be synchronized ?

/**
 * This class acts as a client for the event server and contains the necessary methods for run sequence related instrumentation
 */
public class Failify {
    private static Failify instance;

    private final String hostname;
    private final String port;
    private final StackMatcher stackMatcher;
    // this is needed because each pass of a method can only be blocked once per thread
    private ThreadLocal<Boolean> allowBlocking;

    /**
     * This method returns an instance of Failify class initialized with ip and port from the env
     */
    public static Failify getInstance() {
        if (instance == null) {
            // the event server ip an port should come from the env vars if not given as args
            instance = new Failify(System.getenv("FAILIFY_EVENT_SERVER_IP_ADDRESS"),
                    System.getenv("FAILIFY_EVENT_SERVER_PORT_NUMBER"));
        }

        instance.initializeAllowBlocking();

        return instance;
    }

    /**
     * Private Constructor
     * @param hostname the hostname or ip address of the event server
     * @param port the port number for the event server
     */
    private Failify(String hostname, String port) {
        this.hostname = hostname;
        this.port = port;
        this.stackMatcher = new StackMatcher();
        this.allowBlocking = new ThreadLocal<>();
    }

    /**
     * This method initializes the Failify singleton instance with the given ip and port
     * @param hostname the hostname or ip address of the event server
     * @param port the port number for the event server
     */
    public static void configure(String hostname, String port) {
        if (instance == null) {
            instance = new Failify(hostname, port);
        }

        instance.initializeAllowBlocking();
    }

    /**
     * this method initializes the thread local allow blocking attribute so each thread is allowed to block for the first time
     */
    private void initializeAllowBlocking() {
        if (allowBlocking.get() == null) {
            allowBlocking.set(true);
        }
    }

    /**
     * Sets allow blocking to true and should be called in the beginning of each instrumented method
     */
    public void allowBlocking() {
        allowBlocking.set(true);
    }

    /**
     * This method enforces the order for an internal event. It first checks if the event is already satisfied or not.
     * then, if the stack matches, the current thread is allowed to be blocked and the blocking condition is satisfied,
     * it blocks the thread until the event dependencies are satisfied. Then, it will mark the event as satisfied in the
     * event server and disallows blocking for the current thread
     * @param eventName that needs to be enforced
     * @param stack the stack trace to match in order to allow blocking
     */
    public void enforceOrder(String eventName, String stack) {
        // check if event is not already sent - useful when resetting a node
        if (!isEventAlreadySent(eventName)) {
            if (stack == null || stackMatcher.match(stack)) {
                // check if blocking is allowed in the current pass
                if (allowBlocking.get()) {
                    // check if blocking condition is satisfied
                    if (isBlockingConditionSatisfied(eventName)) {
                        blockAndPoll(eventName);
                        sendEvent(eventName);
                        allowBlocking.set(false);
                    }
                }
            }
        }
    }

    /**
     * This method enforces a garbage collection event. It should be called in the beginning of the main method. Then,
     * it will create a thread that checks that event is not sent yet, then, is the blocking condition is satisfied, it
     * will block the created thread until the event dependencies are satisfied. After getting unblocked, it will run the
     * gc and mark the event as satisfied in the event server.
     * @param eventName that needs to be enforced
     */
    public void garbageCollection(String eventName) {
        Thread gcThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // check if event is not already sent - useful when resetting a node
                if (!isEventAlreadySent(eventName)) {
                    // check if blocking condition is satisfied
                    if (isBlockingConditionSatisfied(eventName)) {
                        blockAndPoll(eventName);
                        System.gc();
                        sendEvent(eventName);
                    }
                }
            }
        });

        gcThread.start();
    }

    /**
     * Sends a message to event server to check if the event has been marked as satisfied or not.
     * @param eventName that needs to be checked
     * @return true if the event is marked as satisfied, otherwise false
     */
    private boolean isEventAlreadySent(String eventName) {
        try {
            URL url = new URL("http://" + hostname + ":" + port + "/events/" + eventName);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            if (connection.getResponseCode() == 200) {
                return true;
            }
            return false;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sends a message to event server to check if the blocking condition for the given event is satisfied or not.
     * @param eventName that needs to be checked
     * @return true if the blocking condition is marked as satisfied, otherwise false
     */
    private boolean isBlockingConditionSatisfied(String eventName) {
        try {
            URL url = new URL("http://" + hostname + ":" + port + "/blockDependencies/" + eventName);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            if (connection.getResponseCode() == 200) {
                return true;
            }
            return false;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This method, in an infinite loop, sends a message to event server and checks that the dependency of the given event
     * are satisfied. When the dependencies finally get satisfied, the method will return
     * @param eventName that needs to be checked
     */
    public void blockAndPoll(String eventName) {
        try {
            blockAndPoll(eventName, false, null);
        } catch (TimeoutException e) {
            // This never happens
        }
    }

    /**
     * This method, in an infinite loop, sends a message to event server and checks that the dependency of the given event
     * (and the event itself if include event flag is true) are satisfied. When the dependencies finally get satisfied, the method will return
     * @param eventName that needs to be checked
     * @param includeEvent the flag to check if the event itself is satisfied or not
     */
    public void blockAndPoll(String eventName, Boolean includeEvent) {
        try {
            blockAndPoll(eventName, includeEvent, null);
        } catch (TimeoutException e) {
            // this never happens
        }
    }

    /**
     * This method, until gets timeout, sends a message to event server and checks that the dependency of the given event
     * are satisfied. When the dependencies finally get satisfied, the method will return
     * @param eventName that needs to be checked
     * @param includeEvent the flag to check if the event itself is satisfied or not
     * @param timeout amount in seconds
     */
    public void blockAndPoll(String eventName, Boolean includeEvent, Integer timeout) throws TimeoutException {

        if (timeout != null) {
            timeout = timeout * 1000;
        }

        while (timeout == null || timeout > 0) {
            try {
                Integer eventInclusion = includeEvent? 1:0;
                URL url = new URL("http://" + hostname + ":" + port + "/dependencies/" + eventName
                        + "?includeEvent=" + eventInclusion);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                if (connection.getResponseCode() == 200) {
                    break;
                }
                Thread.sleep(5);

                if (timeout != null) {
                    timeout -= 5;
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (timeout != null && timeout <= 0) {
            throw new TimeoutException("The timeout for event " + eventName + " is passed");
        }
    }

    /**
     * Sends a message to event server and marks the event as satisfied.
     * @param eventName the event to be marked as satisfied
     */
    public void sendEvent(String eventName) {
        try {
            URL url = new URL("http://" + hostname + ":" + port + "/events");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            String input = "{\"name\":\"" + eventName + "\"}";

            OutputStream os = connection.getOutputStream();
            os.write(input.getBytes());

            connection.getResponseCode();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
