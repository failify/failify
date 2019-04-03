# Failify

[![Javadocs](https://www.javadoc.io/badge/io.failify/failify.svg)](https://www.javadoc.io/doc/io.failify/failify) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Maven Central](https://img.shields.io/maven-central/v/io.failify/failify.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.failify%22%20AND%20a%3A%22failify%22)

Failify is a test framework for end-to-end testing of distributed systems. It can be used to deterministically inject failures during a normal test case execution. Currently, node failure, network partition, network delay, network packet loss, and clock drift is supported. For a few supported languages, it is possible to enforce a specific order between nodes in order to reproduce a specific time-sensitive scenario and inject failures before or after a specific method is called when a specific stack trace is present. For more information, please refer to the documentation

# Questions

To get started using Failify, look at the Failify's [User Guide](https://docs.failify.io) for your desired version. For detailed information about Failify's API take a look at its [Javadoc](https://www.javadoc.io/doc/io.failify/failify). If you still have questions, try asking in StackOverflow using failify tag or create a github issue with question label.

# Contributing

Contribution to Failify is welcomed and appreciated. For developer guides check out the project's [wiki](https://github.com/failify/failify/wiki). To contribute, you first need to find an open issue in the project [issues page](https://github.com/failify/failify/issues). If what you want to work on is not listed as an issue, first create an issue and discuss the importance and necessity of what you want to contribute. Then, send a pull request to be reviewed. If approved, your pull request will be merged into develop and will be included in the next release! Yaaaaay!

# License

Failify is licensed under [MIT](https://opensource.org/licenses/MIT) and is freely available on Github.