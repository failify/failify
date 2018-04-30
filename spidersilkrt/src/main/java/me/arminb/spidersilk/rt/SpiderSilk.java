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

package me.arminb.spidersilk.rt;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class SpiderSilk {
    private static SpiderSilk instance;

    private final String hostname;
    private final String port;
    private final StackMatcher stackMatcher;
    // this is needed because each pass of a method can only be blocked once per thread
    private ThreadLocal<Boolean> allowBlocking;

    public static SpiderSilk getInstance() {
        return instance;
    }

    private SpiderSilk(String hostname, String port) {
        this.hostname = hostname;
        this.port = port;
        this.stackMatcher = new StackMatcher();
        this.allowBlocking = new ThreadLocal<>();
        this.allowBlocking.set(true);
    }

    public static void configure(String hostname, String port) {
        if (instance == null) {
            instance = new SpiderSilk(hostname, port);
        }
    }

    public void allowBlocking() {
        allowBlocking.set(true);
    }

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

    // This method blocks until the dependencies of eventName are met not the event itself
    public void blockAndPoll(String eventName) {
        blockAndPoll(eventName, false);
    }
    public void blockAndPoll(String eventName, Boolean includeEvent) {
        while (true) {
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
                Thread.sleep(1);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

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
