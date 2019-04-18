=========
Changelog
=========

v 0.2.1 (04/17/2019)
=======
* Fixes NPE when service type is not specified
* Changes the return value of the runtime's ip method when using Docker Toolbox from localhost to VM's ip address

v 0.2.0 (04/02/2019)
=======

* Ability to impose network delay and loss in a node
* Scala support for Run Sequence Instrumentation Engine
* The runner is now thread-safe and multiple test cases can run in parallel and in different threads
* Stack matcher now matches against the right order of traces in a stack trace instead of the exact given indices
* New nodes can be added based on a pre-defined service and using a limited node builder after a deployment is started
