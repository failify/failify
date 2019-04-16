===============================
Running |projectName| in Docker
===============================


Why it may be needed?
=====================

There could be two reasons that you want to run |projectName| test cases in a Docker container:

* Your CI nodes are Docker containers and you don't have any other options
* You are developing in a non-Linux operating system (e.g. MacOS or Windows) and the final binary is native to your build
  environment. As such, you are not able to run the built artifact in a docker container which is Linux-based. This will
  require doing the whole build for testing inside a container.
* Your client needs to access the nodes using their hostname or on any port number (without exposing them).
  Either of these cases requires the client to be in the same network namespace as the nodes and that is only
  possible if you run |projectName| in a Docker container.


How to do this?
===============

1. Create a docker image for running your test cases. That image should at least include Java 8+. You may want
   to install a build system like Maven as well. Also, install any other packages or libraries which are needed for your test
   cases to run and are already installed in your machine. In case you need instrumentation for your test cases, install
   the required packages for your specific instrumentor as well.

.. code-block:: docker

    FROM maven:3.6.0-jdk-8
    ADD /path/to/aspectj
    ENV ASPECTJ_HOME="/path/to/aspectj"

2. Change the current directory to your project's root directory. Start a container from the created image with the
following docker run arguments:

    * Share your project's root directory with the container (``-v $(pwd):/path/to/my/project``)
    * Make the project's root directory mapped path the working directory in the container (``-w /path/to/my/project``)
    * Share the docker socket with the container (``-v /var/run/docker.sock:/var/run/docker.sock``)

Your final command to start the container should be something like this:

.. code-block:: shell

    $  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd):/path/to/my/project
    -w /path/to/my/project myImage:1.0 mvn verify