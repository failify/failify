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
    private final Integer port;
    private final StackMatcher stackMatcher;

    public static SpiderSilk getInstance() {
        return instance;
    }

    private SpiderSilk(String hostname, Integer port) {
        this.hostname = hostname;
        this.port = port;
        this.stackMatcher = new StackMatcher();
    }

    public static void configure(String hostname, Integer port) {
        if (instance == null) {
            instance = new SpiderSilk(hostname, port);
        }
    }

    public void enforceOrder(String eventName, String stack) {
        if (stackMatcher.match(stack)) {
            blockAndPoll(eventName);
            sendEvent(eventName);
        }
    }

    public void garbageCollection(String eventName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                blockAndPoll(eventName);
                System.gc();
            }
        });
    }

    public void blockAndPoll(String eventName) {
        while (true) {
            try {
                URL url = new URL("http://" + hostname + ":" + port + "/dependencies/" + eventName);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                if (connection.getResponseCode() == 200) {
                    break;
                }
                Thread.sleep(10);
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
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            String input = "{\"name\":\"" + eventName + "\"}";

            OutputStream os = connection.getOutputStream();
            os.write(input.getBytes());
            os.flush();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
