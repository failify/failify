=========
Changelog
=========

v 0.2.0
=======

* Ability to impose network delay and loss in a node
* Scala support for Run Sequence Instrumentation Engine
* The runner is now thread-safe and multiple test cases can run in parallel and in different threads
* Stack matcher now matches against the right order of traces in a stack trace instead of the exact given indices
* New nodes can be added based on a pre-defined service and using a limited node builder after a deployment is started
