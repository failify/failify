===============================
Deterministic Failure Injection
===============================

Although injecting a failure by calling a method in the middle of a test case is suitable for many of the scenarios, there
exists scenarios where it is needed to inject failures in a very specific moment. With |projectName|, for a few supported
languages, it is possible to inject a failure right before or after a method call where a specific stack trace is present.
This happens through defining a set of named internal and test case events, ordering those events in a run sequence
string, and let the |projectName|'s runtime engine enforce the specified order between the nodes.

Internal Events
===============

Internal events are the ones that happen inside a node. Realizing internal events requires binary instrumentation, and as such,
is only supported for a few programming languages. You can find more information in :doc:`runseq` page.
Available internal events are:

* **Scheduling Event**: This event can be of type *BLOCKING* or *UNBLOCKING* and can happen before or after a specific
  stack trace. The stack trace should come from a stack trace event definition. When defining this kind of events, the
  definition should be a pair of blocking and unblocking events. Basically, make sure to finally unblock everything that
  has been blocked. This event is useful when it is needed to block all the threads for a specific stack trace, do some
  other stuff or let the other threads make progress, and then, unblock the blocked threads.

.. code-block:: java

    .withNode("n1", "service1")
        .withSchedulingEvent("bast1")
            .after("st1") // The name of the stack trace event. An example comes later
            .operation(SchedulingOperation.BLOCK)
        .and()
        .withSchedulingEvent("ubast1")
            .after("st1")
            .operation(SchedulingOperation.UNBLOCK)
        .and()
        // The same events using shortcut methods
        .blockAfter("bast1", "st1")
        .unblockAfter("ubast1", "st1")
    .and()

* **Stack Trace Event**: This event is kind of like a scheduling event except that nothing happens between blocking and
  unblocking. All the threads with the defined stack trace will be blocked until the dependencies of the event are
  satisfied (based on the defined run sequence). The blocking can happen before or after a method. This event can act as
  an indicator that the program has reached a specific method with a specific stack trace. To specify the stack traces,
  the default is to have a list of method signatures with ``[package].[class].[method]`` where the last called method comes
  at the end. As some languages may not have the concept of class or package, you may want to check :doc:`runseq` as well
  for additional instructions for specific languages.

  It is important to note that, the method signatures are not required to be present exactly in the given indices in the
  current stack trace. Only the right order of appearance is sufficient.


.. code-block:: java

    .withNode("n1", "service1")
        .withStackTraceEvent("st1")
            .trace("io.failify.Hello.worldCaller")
            .trace("io.failify.Hello.world")
            .blockAfter().and()
        // The same event using a shortcut method
        .stackTrace("st1", "io.failify.Hello.worldCaller,io.failify.Hello.world", true)
    .and()

* **Garbage Collection Event**: This event is for invoking the garbage collector for supported languages e.g. Java.

.. code-block:: java

    withNode("n1", "service1").
        .withGarbageCollectionEvent("gc1").and()
    and()

Test Case Events
================

Test case events are the connection point between the test case and the |projectName|'s runtime engine. Internal events'
orders are enforced by the runtime engine, but it is the test case responsibility to enforce the test case events if they
are included in the run sequence.

.. code-block:: java

    new Deployment.Builder("sample")
        .testCaseEvents("tc1","tc2")

The Run Sequence
================

Finally after defining all the necessary events, you should tie them together in the run sequence by using event names
as the operands, ``*`` and ``|`` as operators and parenthesis. ``*`` and ``|`` indicate sequential and parallel execution
respectively.

.. code-block:: java

    new Deployment.Builder("sample")
        .runSeq("bast1 * tc1 * ubast1 * (gc1 | x1)")

This run sequence blocks all the threads in node ``n1`` with the stack trace of event ``st1`` (``bast1``), waits for the
test case to enforce ``tc1``, unblcoks the blocked threads in node ``n1`` (``ubast1``), and finally, in parallel, performs
a garbage collection in ``n1`` (``gc1``) and kills node ``n2`` (``x1``).

At any point, a test can use the ``FailifyRunner`` object to enforce the order of a test case event. Enforcement of a test case
event in the test case is only needed if something is needed to be done when the event dependencies are satisfied, e.g.
injecting a failure.

.. code-block:: java

    runner.runtime().enforceOrder("tc1", 10, () -> runner.runtime().clockDrift("n1", -100));

Here, when the dependencies of event ``tc1`` are satisified, a clock drift in the amount of -100ms will be applied to node
``n1``, and ``tc1`` event will be marked as satisfied. If after 10 seconds the dependencies of ``tc1`` are not satisfied,
a ``TimeoutException`` will be thrown. If the only thing that the test case needs is to wait for an event or its
dependencies to be satisfied the ``waitFor`` method can be used.

.. code-block:: java

    runner.runtime().waitFor("st1", 10);

Here again, if the event dependecies are not satisfied in 10 seconds, a ``TimeoutException`` will be thrown.

