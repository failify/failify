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
 */

package io.failify.samples.multithread;

import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Random;

public class Main {
    private static Main instance;
    public static Main getInstance() {
        return instance;
    }

    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        new Thread(()-> fileNameUtil()).start();
        new Thread(()-> helloWorld1()).start();
        new Thread(()-> {helloWorld2(); helloWorld2();}).start();
        new Thread(()-> helloWorld3()).start();

        try {
            Files.write(Paths.get("/var/log/sample1"), "This is a var/log/sample1!".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            Files.write(Paths.get("/var/log/samples/sample2"), "This is a var/log/samples/sample2!".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            Files.write(Paths.get("/failify/shared"), "I am the best shared file ever!".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void helloWorld1() {
        try {
            Thread.sleep(new Random().nextInt(50));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Hello World 1!");
        System.out.println(new Date());
    }


    public void helloWorld2() {
        try {
            Thread.sleep(new Random().nextInt(50));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Hello World 2!");
    }

    public void helloWorld3() {
        try {
            Thread.sleep(new Random().nextInt(50));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Hello World 3!");
        System.out.println(new Date());
    }

    public void fileNameUtil() {
        System.out.println(FilenameUtils.normalize("/a/b/../c", true));
    }
}
