===========
Quick Start
===========

|projectName| is a Java-based end-to-end testing framework. So, you will need to write your test cases In Java, or languages that
can use Java libraries like the ones that can run on JVM, e.g. Scala. |projectName| can be used alongside the popular testing
frameworks in your programming language of choice e.g. JUnit in Java. Here, we use Java and JUnit . We also use Maven as
the build system.

Adding dependencies
===================

First, create a simple Maven application and add |projectName|'s dependency to your pom file.

.. ifconfig:: version.endswith("SNAPSHOT")

    .. code-block:: xml

        <repositories>
            <repository>
                <id>oss.sonatype</id>
                <url>http://oss.sonatype.org/content/repositories/snapshots</url>
                <releases>
                    <enabled>false</enabled>
                </releases>
                <snapshots>
                    <enabled>true</enabled>
                </snapshots>
            </repository>
        </repositories>

.. parsed-literal::

    <dependency>
        <groupId>io.failify</groupId>
        <artifactId>failify</artifactId>
        <version>\ |release|\ </version>
    </dependency>

Also add failsafe plugin to your pom file to be able to run integration tests.

.. code-block:: xml

    <project>
      [...]
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.0.0-M3</version>
            <executions>
              <execution>
                <goals>
                  <goal>integration-test</goal>
                  <goal>verify</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
      [...]
    </project>

Creating a Dockerfile
=====================

Next, you need to create a Dockerfile for your application and that Dockerfile should add any dependency that may be
needed by your application. In case you want to use the network partition capability
of |projectName|, you need to install ``iptables`` package as well. Network delay and loss will also need the ``iproute``
package to be installed. Here, we assume the application under test is written in Java.
So, we create a Dockerfile in the ``docker/Dockerfile`` address with the following content:

.. code-block:: docker

    FROM java:8-jre
    RUN apt update && apt install -y iptables iproute

.. important::

    In case you are using Docker Toolbox (and consequently boot2docker) on Windows or Mac, be aware that your currently
    installed boot2docker image may be missing ``sched_netem`` kernel module which is included in most of the
    linux distributions and is needed for ``tc`` command in the ``iproute`` package to work. So, unless you upgrade your
    boot2docker image (normally through running ``docker-machine upgrade [machine_name]``, you won't be able to use the
    network operation capabilities of |projectName|.

Adding a Test Case
==================

Now, create a JUnit integration test case  (ending with IT so failsafe picks it up) in the project's test directory. Here,
we are assuming the final distribution of the project is a zipfile in the Maven's ``target`` directory. Also, we are assuming
the zip file contains a ``project-[PROJECT_VERSION]`` directory and that directory itself contains a ``bin``
directory which contains a ``start.sh`` file to start the application.

.. code-block:: java
    :linenos:

    public class SampleTestIT {
        protected static FailifyRunner runner;

        @BeforeClass
        public static void before() throws RuntimeEngineException {
            String projectVersion = "0.2.1";
            FailifyRunner runner = Deployment.builder("sampleTest")
                // Service Definition
                .withService("service1")
                    .appPath("target/project.zip", "/project", PathAttr.COMPRESSED)
                    .startCmd("/project/project-" + projectVersion + "/bin/start.sh -conf /config.cfg")
                    .dockerImg("project/sampleTest:" + projectVersion)
                    .dockerFile("docker/Dockerfile", false)
                    .tcpPort(8765)
                    .serviceType(ServiceType.JAVA).and()
                // Node Definitions
                .withNode("n1", "service1")
                    .applicationPath("config/n1.cfg", "/config.cfg".and()
                .withNode("n2", "service1")
                    .applicationPath("config/n2.cfg", "/config.cfg".and()
                .withNode("n3", "service1")
                    .applicationPath("config/n3.cfg", "/config.cfg".and()
                .build().start();
        }

        @AfterClass
        public static void after() {
            if (runner != null) {
                runner.stop();
            }
        }

        public void test1() throws RuntimeEngineException {
            ProjectClient client = ProjectClient.from(runner.runtime().ip("n1"),
                runner.runtime().portMapping("n1", 8765, PortType.TCP));
            ..
            runner.runtime().clockDrift("n1", 100);
            ..
            runner.runtime().networkPartition(NetPart.partitions("n1", "n2", "n3")
                .connect(1,3));
            ..
            runner.runtime().networkOperation("n2", NetOp.delay(100).jitter(10),
                 NetOp.loss(10));
            ..
        }
    }

Each |projectName| test case should start with defining a new ``Deployment`` object. A deployment definition consists of a a set
of service and node definitions. A Service is a node template and defines the docker image for the node, the start bash
command, required environment variables, common paths, etc. for a specific type of node. For additional info about available
options for a service check :javadoc:`ServiceBuilder's JavaDoc </io/failify/dsl/entities/Service.Builder.html>`.

Line 9-16 defines ``service1`` service. Line 10 adds the zip file to the service at the ``/project`` address and also
marks it as compressed so |projectName| decompresses it before adding it to the node (**In Windows and Mac, you should make sure
the local path you are using here is shared with the Docker VM**). Line 11 defines the start command for the
node, and in this case, it is using the ``start.sh`` bash file and it feeding it with ``-conf /config.cfg`` argument. This
config file will be provided separately through node definitions later. Line 15 marks tcp port ``8765`` to be exposed for the
service. This is specially important when using |projectName| in Windows and Mac as the only way to connect to the Docker containers
in those platforms is through port forwarding. Line 16 concludes the service definition by marking it as a Java application.
If the programming language in use is listed in ``ServiceType`` enum, make sure to mark your application with the right
``ServiceType``.

.. important:: If your program runs on JVM and your programming language in use is not listed in  the ``ServiceType``
          enum, just choose ``ServiceType.Java`` as the service type.

Lines 18-23 defines two nodes named ``n1``, ``n2`` and ``n3`` from ``service1`` service and is adding a separate local config file
to each of them which will be located at the same target address ``/config.cfg``. Most of the service configuration can be
overriden by nodes. For more information about available options for a node check
:javadoc:`Node Builder's JavaDoc </io/failify/dsl/entities/Node.Builder.html>`.

Line 26 starts the defined deployment and line 32 stops the deployment after all tests are executed.

Line 37-38 shows how the ``runner`` object can be used to get the ip address and port mappings for each node to be potentially
used by a client. Line 40 shows a simple example of how |projectName| can manipulate the deployed environment by just a method call. In
this case, a clock dirft of 100ms will be applied to node ``n1``. Line 42 shows how a network partition can be defined
and imposed. Here, each of the nodes will be in a separate partition and the first (``n1``) and third (``n3``) partition will be
connected together. Line 45 shows an example of imposing network delay and loss on all the interfaces of a specific node.
Here, a network delay from a uniform distribution with mean=100 and variance=10 will be applied on ``n2`` and 10% of the
packets will be lost. For more information about available runtime
manipulation operations check :javadoc:`LimitedRuntimeEngine's JavaDoc </io/failify/execution/LimitedRuntimeEngine.html>`.

Logger Configuration
====================

|projectName| uses SLF4J for logging. As such, you can configure your logging tool of choice. A sample configuration with
Logback can be like this:

.. code-block:: xml

    <?xml version="1.0" encoding="UTF-8"?>
    <configuration>
        <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
            <layout class="ch.qos.logback.classic.PatternLayout">
                <Pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</Pattern>
            </layout>
        </appender>

        <logger name="io.failify" level="DEBUG"/>

        <root level="ERROR">
            <appender-ref ref="Console" />
        </root>
    </configuration>

Running the Test Case
=====================

Finally, to run the test cases, run the following bash command:

.. code-block:: bash

    $  mvn clean verify

