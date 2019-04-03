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
