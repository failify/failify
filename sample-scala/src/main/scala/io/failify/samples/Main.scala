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
package io.failify.samples

import java.io.IOException
import java.nio.file.{Files, Paths}
import java.util.{Date, Random}

import org.apache.commons.io.FilenameUtils

/**
 * @author ${user.name}
 */
object Main {

  def main(args: Array[String]): Unit = {
    start
  }

  def start(): Unit = {
    new Thread(() => fileNameUtil()).start()
    new Thread(() => helloWorld1()).start()
    new Thread(() => {
      def foo() = {
        helloWorld2()
        helloWorld2()
      }

      foo()
    }).start()
    new Thread(() => helloWorld3()).start()
    try
      Files.write(Paths.get("/var/log/sample1"), "This is a var/log/sample1!".getBytes)
    catch {
      case e: IOException =>
        e.printStackTrace()
    }
    try
      Files.write(Paths.get("/var/log/samples/sample2"), "This is a var/log/samples/sample2!".getBytes)
    catch {
      case e: IOException =>
        e.printStackTrace()
    }
    try
      Files.write(Paths.get("/failify/shared"), "I am the best shared file ever!".getBytes)
    catch {
      case e: IOException =>
        e.printStackTrace()
    }
  }

  def helloWorld(msg: String): Unit = {
    hello(msg)
  }

  def hello(msg: String): Unit = {
    println(msg)
  }



  def helloWorld1(): Unit = {
    try
      Thread.sleep(new Random().nextInt(50))
    catch {
      case e: InterruptedException =>
        e.printStackTrace()
    }
    helloWorld("Hello World 1!")
  }


  def helloWorld2(): Unit = {
    try
      Thread.sleep(new Random().nextInt(50))
    catch {
      case e: InterruptedException =>
        e.printStackTrace()
    }
    helloWorld("Hello World 2!")
  }

  def helloWorld3(): Unit = {
    try
      Thread.sleep(new Random().nextInt(50))
    catch {
      case e: InterruptedException =>
        e.printStackTrace()
    }
    helloWorld("Hello World 3!")
  }

  def fileNameUtil(): Unit = {
    println(FilenameUtils.normalize("/a/b/../c", true))
  }

}
