package io.failify.samples

import java.util.concurrent.TimeoutException

import io.failify.FailifyRunner
import io.failify.dsl.entities.{Deployment, Node, ServiceType}
import io.failify.exceptions.{DeploymentVerificationException, RuntimeEngineException, WorkspaceException}
import io.failify.execution.{NetOp, NetPart}
import org.junit.Test

class MainTest {
  @Test
  @throws[DeploymentVerificationException]
  @throws[RuntimeEngineException]
  @throws[TimeoutException]
  @throws[WorkspaceException]
  def simpleDefinition(): Unit = {
    val deployment = Deployment.builder("sample-multithread")
      .withServiceFromJvmClasspath("s1", "target/classes", "**commons-io*.jar")
        .startCommand("java -cp ${FAILIFY_JVM_CLASSPATH} io.failify.samples.Main")
        .dockerImageName("failify/scala")
        .dockerFileAddress("docker/Dockerfile", false)
        .logFile("/var/log/sample1")
        .logDirectory("/var/log/samples")
        .serviceType(ServiceType.SCALA).and
      .withNode("n1", "s1")
        .stackTrace("e1", "io.failify.samples.Main$.helloWorld1,io.failify.samples.Main$.hello")
        .stackTrace("e2", "io.failify.samples.Main$.helloWorld2,io.failify.samples.Main$.helloWorld")
        .stackTrace("e3", "io.failify.samples.Main$.helloWorld3,io.failify.samples.Main$.hello")
        .stackTrace("e4", "org.apache.commons.io.FilenameUtils.normalize")
        .blockBefore("bbe2", "e2")
        .unblockBefore("ubbe2", "e2")
        .garbageCollection("g1").and.withNode("n2", "s1")
        .offOnStartup.and
      .testCaseEvents("x1", "x2")
      .runSequence("bbe2 * e1 * ubbe2 * x1 * e2 * e3 * x2 * e4")
      .sharedDirectory("/failify")
      .build

    val runner = FailifyRunner.run(deployment)
    // Starting node n2
    runner.runtime.enforceOrder("x1", 15, () => runner.runtime.startNode("n2"))
    // Applying network delay and loss on node n2 before restarting it
    runner.runtime.networkOperation("n2", NetOp.delay(50).jitter(10), NetOp.loss(30))
    // removing the first network partition and restarting node n2
    runner.runtime.enforceOrder("x2", 10,
      () => runner.runtime.restartNode("n2", 10)
    )
    // Waiting for the run sequence to be completed
    runner.runtime.waitForRunSequenceCompletion(60, 20)
  }
}
