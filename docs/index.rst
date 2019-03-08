.. Failify documentation master file, created by
   sphinx-quickstart on Tue Feb  5 15:05:10 2019.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

.. Replacements Definition


=======
Failify
=======

Introduction
============

|projectName| is a test framework for end-to-end testing of distributed systems. It can be used to deterministically inject
failures during a normal test case execution. Currently, node failure, network partition, network delay, network packet loss, and clock drift is supported. For a few supported languages, it is possible to enforce a specific order between
nodes in order to reproduce a specific time-sensitive scenario and inject failures before or after a specific method is
called when a specific stack trace is present.





.. toctree::
    :caption: Table of Contents
    :maxdepth: 2
    :glob:

    pages/prereq
    pages/quickstart
    pages/deterministic
    pages/runseq
    pages/jvmservice
    pages/docker