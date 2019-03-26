============================
Adding New Nodes Dynamically
============================

It is possible to add new nodes dynamically after a defined deployment is started. New nodes can only be created out of
pre-defined services and they can't include any internal events. In the following code, ``service1`` service is first created
similar to the one in :doc:`quickstart`. Then, at line 31, a new node named ``n2`` is being created out of ``service1`` service.
``Node.limitedBuilder`` method returns an instance of ``Node.LimitedBuilder`` which then can be further customized by chaining the proper
method calls. This builder wouldn't allow the definition of internal events for the node. However, all the other node configurations
are available.

.. code-block:: java
    :linenos:

    public class SampleTestIT {
        protected static FailifyRunner runner;

        @BeforeClass
        public static void before() throws RuntimeEngineException {
            String projectVersion = "0.2.1";
            Deployment deployment = Deployment.builder("sampleTest")
                // Service Definition
                .withService("service1")
                    .applicationPath("target/project.zip", "/project", PathAttr.COMPRESSED)
                    .startCommand("/project/bin/start.sh")
                    .dockerImage("project/sampleTest:" + projectVersion)
                    .dockerFileAddress("docker/Dockerfile", false)
                    .serviceType(ServiceType.JAVA).and()
                // Node Definitions
                .withNode("n1", "service1").and()
                .build();

            FailifyRunner runner = FailifyRunner.run(deployment);
        }

        @AfterClass
        public static void after() {
            if (runner != null) {
                runner.stop();
            }
        }

        public void test1() throws RuntimeEngineException {
            ..
            runner.addNode(Node.limitedBuilder("n2", "s1"));
            ..
        }
    }

The current limitation of this capability is that if there is a network partition applied to the current deployment, the
new node wouldn't be included in that network partition. Introduction of new network partitions will include the new node
in generating blocking rules for iptables. This limitation will be removed in future releases.