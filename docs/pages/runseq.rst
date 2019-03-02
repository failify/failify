===================================
Run Sequence Instrumentation Engine
===================================

|projectName|'s deterministic failure injection requires binary instrumentation. Different programming languages require
different instrumentors, and thus, if you are going to use this feature, you need to specify the programming language for
involved services.

.. code-block:: java

    .withService("service1")
        .serviceType(ServiceType.JAVA)

Next, for each service, you may need to mark some paths as library or instrumentable paths. Check specific language
instructions as this may differ based on the programming language in use.

Java
====

`AspectJ <https://www.eclipse.org/aspectj/>`_ is used for Java instrumentation. AspectJ 1.8+ should work perfectly with
|projectName|. You need to install Aspectj on your machine and expose ``ASPECTJ_HOME`` environment variable pointing to the
home directory of AspectJ in your machine. Also, you need to include AspectJ and |projectName| runtime dependencies to your
project. Example dependencies to be added to your pom file with AspectJ 1.8.12 are as follows:

.. parsed-literal::

    <dependency>
        <groupId>io.failify</groupId>
        <artifactId>failifyrt</artifactId>
        <version>\ |version|\ </version>
    </dependency>
    <dependency>
        <groupId>org.aspectj</groupId>
        <artifactId>aspectjrt</artifactId>
        <version>1.8.12</version>
    </dependency>

Finally, you need to mark:

* all of the required jar files or class file directories to run your application as **library path**.
* all of the jar files or class file directories which contain a method included as the last method in one of the stack
  trace events as **instrumentable path**

.. code-block:: java

    .withService("service1")
        .applicationPath("./projectFiles", "/project")
        // It is possible to use wildcard paths for marking library paths
        .libraryPath("/project/libs/*.jar") // This is a target path in the node.
        .applicationPath("target/classes", "/project/libs/classes", PathAttr.LIBRARY)
        .applicationPath("./extraLib.jar", "/project/libs/extraLib.jar", PathAttr.LIBRARY)
        .instrumentablePath("/project/libs/main.jar") // This is a target path in the node
        .instrumentablePath("/project/libs/classes")
    .and()