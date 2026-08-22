# Technical Architecture Report: Per-Test Code Coverage Attribution in Parallel Java Executions

## Executive Overview & The Global Agent State Dilemma

Capturing code coverage on a per-test basis in Java environments
requires mapping specific bytecode execution paths to individual test
method invocations. Standard code coverage analysis measures aggregate
coverage across an entire test suite execution run. Attempting to
extract single-test coverage mapping using standard bytecode
instrumentation agents like JaCoCo introduces significant state
isolation challenges when tests execute concurrently^1^.

JaCoCo achieves low-overhead coverage collection by modifying Java
bytecode at class-loading time^2^. It injects probe activation
instructions into methods, instrumenting branches and basic blocks^2^.
At runtime, these probes flip boolean flags within a static array
allocated once per loaded class^2^. The Java Virtual Machine (JVM)
memory model treats this probe matrix as globally shared state across
all executing threads within the process heap.

In a naive per-test coverage implementation, a JUnit 5
TestExecutionListener intercepts test execution events by invoking
IAgent.reset() immediately before a test method starts to clear the
global probe array, allowing the test method to execute, and invoking
IAgent.getExecutionData(true) immediately after completion to dump and
clear the recorded probes.

This pattern breaks down under parallel test execution, such as JUnit 5
thread pools, Maven Surefire multi-threading, or Gradle parallel worker
threads. Because the probe array is process-global and shared among
threads, concurrent execution creates severe race conditions:

-   **Cross-Contamination**: A thread executing Test A writes to the
    > global probe array while a concurrent thread executing Test B
    > simultaneously writes to the exact same class probe arrays. When
    > Test A finishes and dumps the execution data, its coverage payload
    > contains probe activations triggered by Test B^1^.

-   **Coverage Loss**: Resetting or retrieving execution data with a
    > purge flag at the boundary of Test A clears the global memory
    > array while Test B is mid-execution. Consequently, all execution
    > probes recorded by Test B up to that millisecond are erased,
    > under-reporting coverage for Test B^1^.

Resolving this architectural limitation requires either isolating
execution contexts at the process level, modifying the instrumentation
engine to support thread-indexed probe structures, or introducing
dynamic sequential execution profiles specifically for coverage analysis
runs.

## 1. Teamscale Open-Source Architecture and Parallel Execution Limits

### Bytecode Instrumentation and Endpoint Protocol

The open-source Teamscale Java Profiler (teamscale-jacoco-agent) relies
on JaCoCo\'s core instrumentation engine for bytecode transformation
while replacing standard output sinks with an event-driven runtime
controller^2^. During process initialization, the agent registers a
class transformer with the JVM\'s instrumentation interface^2^. As
classes load, JaCoCo injects probe arrays into the bytecode^2^.

To record testwise coverage, the agent operates an embedded HTTP server
listening for execution lifecycle commands^5^. A test runner plugin or a
custom listener dispatches HTTP POST requests to
/test/start/{uniformPath} when a test begins, and to
/test/end/{uniformPath} upon test completion^6^.

Upon receiving a completion request, the agent queries JaCoCo\'s runtime
memory controller, extracts the current probe state using internal
execution data interfaces, maps those probes to the designated uniform
path identifier, and resets the internal bitsets for the next test^3^.

### Concurrency Handling and Process Constraints

Despite providing a dedicated API for testwise tracking, Teamscale\'s
agent does not support parallel test execution within a single JVM
process^7^. Because Teamscale wraps standard JaCoCo probe arrays, the
underlying probe memory remains process-global^2^.

If multiple tests execute concurrently within the same JVM, overlapping
start and end HTTP calls collide within the agent\'s internal state
machine^6^. When a new start request arrives before the preceding test
has issued its end event, the agent cannot segregate which probe index
was activated by which worker thread^7^.

The agent explicitly detects this out-of-order state overlap^7^. When a
secondary test starts while a prior test is active, the agent logs a
runtime warning, marks the interrupted test execution as skipped, and
invalidates the testwise coverage mapping for that window to prevent
corrupted data from reaching the downstream analysis server^7^.
Consequently, official documentation specifies that builds executing
testwise coverage collection must enforce single-threaded execution per
JVM process^8^.

## 2. Process-Level Isolation: Maven Surefire and Gradle Forking Mechanics

Process-level isolation circumvents JaCoCo\'s global shared memory
limitation by forcing every test class or test method to execute inside
its own isolated JVM process^9^.

### Maven Surefire and Gradle Isolation Configurations

Maven Surefire provides two primary parameters controlling JVM process
lifecycles: forkCount and reuseForks^9^. The forkCount parameter defines
the maximum number of concurrent JVM processes to spawn, while setting
reuseForks=false instructs Surefire to terminate the spawned JVM
immediately after a single test class finishes, instantiating a new JVM
process for the next test class^9^.

When combining forkCount=N with reuseForks=false, tests run in parallel
across ![](media/image1.png){width="0.19547462817147856in"
height="0.26749234470691163in"} operating system processes, but each
individual JVM process executes sequentially with respect to its
allocated test classes^9^. In Gradle, an identical isolation model is
configured on the Test task using maxParallelForks = N alongside
forkEvery = 1.

To attribute execution data (.exec) to specific tests under process
isolation, the JaCoCo Java agent command line is configured dynamically
using tool system properties to ensure each fork writes to a distinct
binary execution file:

> XML
>
> \<plugin\>\
> \<groupId\>org.apache.maven.plugins\</groupId\>\
> \<artifactId\>maven-surefire-plugin\</artifactId\>\
> \<version\>3.5.5\</version\>\
> \<configuration\>\
> \<forkCount\>4\</forkCount\>\
> \<reuseForks\>false\</reuseForks\>\
> \<argLine\>\
> -javaagent:\${settings.localRepository}/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar=destfile=\${project.build.directory}/jacoco-execs/jacoco-\${surefire.forkNumber}-\${sys:surefire.test}.exec\
> \</argLine\>\
> \</configuration\>\
> \</plugin\>

### Isolation Trade-Offs and Architectural Impact

Process-level isolation presents stark trade-offs between absolute data
correctness and build performance^11^. While it completely eliminates
probe cross-contamination, spawning thousands of short-lived JVM
processes introduces massive resource consumption^11^.

The JVM Just-In-Time (JIT) compiler requires warm-up time to optimize
hot paths; destroying the process after every test class forces
continuous baseline bytecode interpretation^11^. Furthermore,
application frameworks such as Spring or Micronaut that rely on static
context caching suffer severe penalties, as context initialization must
re-run for every process fork rather than reusing warm memory
instances^11^.

## 3. JUnit 5 Parallel Execution Config & Dual-Profile Analysis Patterns

JUnit 5 provides native concurrent test execution capabilities via the
JUnit Platform Engine^12^. Parallel execution is enabled globally or
per-suite using configuration parameters typically placed in
configuration files or system properties^12^.

### Limitations of Dynamic Execution Mode Modification

In the JUnit Platform architecture, parallel execution scheduling,
thread pool construction, and execution tree node evaluation modes are
calculated during the Test Discovery and Execution Plan Phase, prior to
the invocation of execution listeners. The callbacks of a
TestExecutionListener fire inside the worker threads allocated by the
scheduler after node execution has begun.

While a listener can inspect the execution context, it cannot
retroactively mutate the execution mode of an active node execution
plan. Attempting to acquire global locks within a listener forces thread
synchronization at runtime, but this introduces thread pool starvation
and severe contention overhead without resolving underlying process
probe race conditions^1^.

### Dual-Profile Build Architecture

The standard architectural pattern separates standard fast verification
builds from specialized coverage analysis runs using build tool
profiles.

> XML
>
> \<project\>\
> \<profiles\>\
> \<!\-- Standard build: Optimized for fast developer feedback via
> parallel execution \--\>\
> \<profile\>\
> \<id\>default-build\</id\>\
> \<activation\>\
> \<activeByDefault\>true\</activeByDefault\>\
> \</activation\>\
> \<properties\>\
> \<surefire.parallel\>all\</surefire.parallel\>\
> \<surefire.threadCount\>4\</surefire.threadCount\>\
> \<junit.jupiter.execution.parallel.enabled\>true\</junit.jupiter.execution.parallel.enabled\>\
> \</properties\>\
> \</profile\>\
> \
> \<!\-- Analysis profile: Enforces sequential execution for exact
> per-test mapping \--\>\
> \<profile\>\
> \<id\>coverage-analysis\</id\>\
> \<properties\>\
> \<surefire.parallel\>none\</surefire.parallel\>\
> \<surefire.forkCount\>1\</surefire.forkCount\>\
> \<surefire.reuseForks\>true\</surefire.reuseForks\>\
> \<junit.jupiter.execution.parallel.enabled\>false\</junit.jupiter.execution.parallel.enabled\>\
> \</properties\>\
> \<build\>\
> \<plugins\>\
> \<plugin\>\
> \<groupId\>org.apache.maven.plugins\</groupId\>\
> \<artifactId\>maven-surefire-plugin\</artifactId\>\
> \<configuration\>\
> \<systemPropertyVariables\>\
> \<junit.jupiter.execution.parallel.enabled\>false\</junit.jupiter.execution.parallel.enabled\>\
> \</systemPropertyVariables\>\
> \</configuration\>\
> \</plugin\>\
> \</plugins\>\
> \</build\>\
> \</profile\>\
> \</profiles\>\
> \</project\>

This dual-profile architecture ensures that routine continuous
integration builds maintain maximum parallel throughput, while dedicated
coverage collection runs execute sequentially under a controlled profile
to produce accurate per-test coverage mappings^12^.

## 4. JaCoCo Core Internals & Feasibility of Session-per-Thread Instrumentation

Understanding why JaCoCo cannot natively isolate probe data per thread
requires examining JaCoCo\'s bytecode modification mechanisms and
runtime memory structures^2^.

### Probes and Probe Array Architecture

When JaCoCo instruments a class, it injects a synthetic static probe
array reference and initialization logic directly into the bytecode^2^.
Probes are written during method execution as direct array assignments
(probes\[index\] = true)^2^. This design avoids volatile memory
barriers, atomic lock operations, or thread lookups, maintaining
execution overhead below typical instrumentation thresholds^2^. However,
because the array is assigned to a static field on the loaded class,
every thread executing methods within that class writes to the same
physical array instance.

JaCoCo provides session metadata management via session information
structures and execution data stores. However, a session identifier in
JaCoCo is purely a metadata string attached to a data dump event.
Setting a session ID does not allocate distinct probe arrays per thread.
When execution data is requested, JaCoCo exports the aggregate state of
the single probe array shared across all threads.

### Technical Feasibility of Thread-Local Probe Storage

Redesigning JaCoCo to support thread-local probe storage would require
replacing static array references with thread-indexed array lookups.
While theoretically possible, this approach introduces major technical
obstacles:

-   **JIT Intrinsification Destructuring**: A standard probe write
    > compiles to a single assembly instruction. Replacing this with
    > thread-local registry lookups introduces map hashing, thread
    > pointer resolution, and register spills on every instrumented
    > branch, drastically increasing CPU overhead.

-   **Memory Footprint Explosion**: A enterprise application loading
    > tens of thousands of classes containing hundreds of thousands of
    > probes requires significant memory. Multiplying these probe arrays
    > across dozens of concurrent worker threads increases heap memory
    > usage exponentially, generating severe garbage collection
    > pressure.

-   **Thread Pool and Asynchronous Context Loss**: Modern Java
    > applications heavily utilize thread pools, asynchronous tasks, and
    > virtual threads. A test method initiating work on Thread A
    > frequently dispatches operations to background worker threads.
    > Thread-local probe storage fails to propagate context across
    > thread boundaries automatically, leading to lost coverage or
    > misattribution.

## 5. Source-Level Instrumentation: OpenClover\'s Attribution Architecture

OpenClover takes a fundamentally different technical approach to
coverage analysis compared to bytecode agents like JaCoCo^14^.

### Abstract Syntax Tree Source Instrumentation

Rather than modifying compiled bytecode at runtime, OpenClover
instruments Java source code prior to compilation^14^. It parses the
abstract syntax tree (AST) and rewrites source files, embedding explicit
tracking hooks directly into statement blocks, decision branches, and
test method entry points^14^.

During compilation, test methods are augmented with explicit test
recorder interceptors^16^. Entering a test method initializes a test
coverage recorder, while executing statements triggers hit notifications
registered in OpenClover\'s coverage database^16^.

### Causes of Parallel Execution Restrictions

OpenClover\'s user documentation explicitly states that per-test
coverage collection does not support parallel test execution^18^. This
constraint is primarily an implementation choice in its runtime recorder
architecture rather than an unresolvable theoretical barrier^16^:

-   **Global Active Recorder Pointer**: OpenClover maintains a global
    > runtime context pointer tracking the active test method^16^. When
    > a test starts, it updates this shared reference^16^. Concurrent
    > test executions overwrite this reference, causing statement hits
    > from one thread to be recorded under the test context of another
    > thread.

-   **Thread Context Overhead Constraints**: OpenClover prioritizes
    > low-latency recording during single-threaded runs^16^. Introducing
    > thread-safe concurrent map lookups to maintain separate recorder
    > stacks per thread thread-id would incur noticeable runtime
    > performance costs and would still suffer from thread-pool context
    > propagation loss during asynchronous execution.

## 6. Architectural Strategy Comparison

The following table synthesizes the primary technical capabilities,
constraints, and performance profiles across the evaluated coverage
collection strategies:

  ---------------------------------------------------------------------------------------------------------------
  **Architectural        **Instrumentation   **Parallel Test   **Attribution      **Build         **Setup
  Strategy**             Level**             Support**         Granularity**      Overhead (5k    Intrusion
                                                                                  Suite)**        Level**
  ---------------------- ------------------- ----------------- ------------------ --------------- ---------------
  **Teamscale Java       Bytecode Agent^2^   Unsupported       Method-Level^6^    Low             Moderate (HTTP
  Profiler**                                 (Fails/Skips on                      (\~1.1x--1.3x   agent +
                                             overlap)^7^                          sequential      listener)^20^
                                                                                  time)           

  **Fork-per-Class       Bytecode Agent^2^   Supported         Class-Level^9^     Catastrophic    Low (Build
  (reuseForks=false)**                       (Isolated via OS                     (5x--20x build  configuration
                                             processes)^9^                        time)^11^       flag)^9^

  **Dual-Profile         Bytecode Agent^2^   Converted to      Method-Level^6^    Moderate        Low (Secondary
  Sequential Run**                           Sequential for                       (\~2x--3x fast  build profile)
                                             Analysis                             parallel time)  

  **Thread-Local JaCoCo  Bytecode Agent      Theoretically     Method-Level       High (3x--10x   High (Requires
  Fork (Hypothetical)**                      Supported                            latency per     custom engine)
                                                                                  thread)         

  **OpenClover AST       Source AST^14^      Unsupported (Data Method-Level^21^   High (Compile   High (Build
  Instrumentation**                          corruption)^18^                      penalty +       pipeline
                                                                                  runtime)^17^    rewrite)^14^
  ---------------------------------------------------------------------------------------------------------------

## 7. Quantitative Performance Model & Recommendation

### Operational Recommendation

For an enterprise system executing an occasional dedicated analysis run
across a suite of **5,000 unit and integration tests**, the recommended
architecture is a **Dual-Profile Build Setup combined with a Sequential
Execution Analysis Run**.

Under this design, standard developer workflows and pull-request
validation pipelines execute in full parallel mode without coverage
agents attached, delivering rapid feedback. Dedicated coverage pipelines
trigger the build using an analysis profile that explicitly disables
test parallelism (forkCount=1, parallel execution disabled) while
attaching the JaCoCo or Teamscale testwise agent^8^.

This setup provides exact method-level coverage attribution, avoids
probe race conditions, eliminates custom agent maintenance, and avoids
the extreme performance costs of process-per-class isolation^9^.

### Quantitative Cost Model: 5,000-Test Suite Analysis

The model evaluates a test suite consisting of 5,000 test methods
distributed across 1,000 test classes (averaging 5 methods per class),
running on an 8-core execution host, with an average application
framework warm-up time of 3 seconds per process boot.

  ----------------------------------------------------------------------------
  **Execution       **Baseline        **Process Isolation    **Dual-Profile
  Metric**          Parallel Run (No  (reuseForks=false)**   Sequential
                    Coverage)**                              Analysis Run**
  ----------------- ----------------- ---------------------- -----------------
  **Concurrent      8 Worker Threads  1,000 Sequentially     1 Long-Running
  Workspaces /                        Spawned JVMs           JVM
  Processes**                                                

  **Process Startup 3.0 Seconds (Paid 3,000.0 CPU Seconds    3.0 Seconds (Paid
  Overhead**        once)             (375s wall-clock)^11^  once)

  **JIT             Fully Optimized   Unoptimized (Reset per Fully Optimized
  Optimization                        class)^11^             (Warm runtime)
  Status**                                                   

  **Data Dump       N/A               Negligible (On JVM     \~10.0 Seconds
  Overhead**                          exit)                  (5,000 memory
                                                             dumps)

  **Estimated Total **\~5 Minutes**   **\~18 to 25 Minutes** **\~18 to 22
  Build Time**                                               Minutes**

  **Attribution     None              Class-Level Only       **Exact
  Correctness                                                Method-Level**
  Status**                                                   
  ----------------------------------------------------------------------------

While process isolation (reuseForks=false) and sequential single-JVM
execution result in similar total wall-clock times, single-JVM
sequential execution provides far higher attribution accuracy
(method-level versus class-level), consumes significantly less CPU and
disk I/O, and preserves application context caching during execution
runs^11^.

#### Alıntılanan çalışmalar

1.  Jacoco Coverage issue with Junit Parallel Execution - Google Groups,
    > [[https://groups.google.com/g/jacoco/c/\_ubGUuBc0xM]{.underline}](https://groups.google.com/g/jacoco/c/_ubGUuBc0xM)

2.  cqse/teamscale-java-profiler - GitHub,
    > [[https://github.com/cqse/teamscale-jacoco-agent]{.underline}](https://github.com/cqse/teamscale-jacoco-agent)

3.  CLAUDE.md - cqse/teamscale-java-profiler - GitHub,
    > [[https://github.com/cqse/teamscale-java-profiler/blob/master/CLAUDE.md]{.underline}](https://github.com/cqse/teamscale-java-profiler/blob/master/CLAUDE.md)

4.  teamscale-java-profiler/agent/README.md at master - GitHub,
    > [[https://github.com/cqse/teamscale-jacoco-agent/blob/master/agent/README.md]{.underline}](https://github.com/cqse/teamscale-jacoco-agent/blob/master/agent/README.md)

5.  Teamscale Java Profiler,
    > [[https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/]{.underline}](https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/)

6.  Teamscale Java Profiler - Testwise Coverage Recording,
    > [[https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/testwise-coverage-recording/]{.underline}](https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/testwise-coverage-recording/)

7.  Releases · cqse/teamscale-java-profiler - GitHub,
    > [[https://github.com/cqse/teamscale-jacoco-agent/releases]{.underline}](https://github.com/cqse/teamscale-jacoco-agent/releases)

8.  Maven Plugin - Teamscale Docs,
    > [[https://docs.teamscale.com/reference/integrations/maven-plugin/]{.underline}](https://docs.teamscale.com/reference/integrations/maven-plugin/)

9.  surefire:test - Apache Maven,
    > [[https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html]{.underline}](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html)

10. Fork Options and Parallel Test Execution -- Maven Surefire Plugin,
    > [[https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html]{.underline}](https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html)

11. JUnit Tests: Why is Maven (Surefire) so much slower than running on
    > Eclipse?,
    > [[https://stackoverflow.com/questions/30751050/junit-tests-why-is-maven-surefire-so-much-slower-than-running-on-eclipse]{.underline}](https://stackoverflow.com/questions/30751050/junit-tests-why-is-maven-surefire-so-much-slower-than-running-on-eclipse)

12. tech.picnic.error-prone-support:error-prone-support:0.15.0 - Maven
    > Central - Sonatype,
    > [[https://central.sonatype.com/artifact/tech.picnic.error-prone-support/error-prone-support/0.15.0]{.underline}](https://central.sonatype.com/artifact/tech.picnic.error-prone-support/error-prone-support/0.15.0)

13. Using JUnit 5 Platform -- Maven Surefire Plugin,
    > [[https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html]{.underline}](https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html)

14. Best 16 Code Coverage Tools to Boost Your Testing in 2026 - TestMu
    > AI,
    > [[https://www.testmuai.com/learning-hub/code-coverage-tools/]{.underline}](https://www.testmuai.com/learning-hub/code-coverage-tools/)

15. OpenClover - GitHub,
    > [[https://github.com/openclover]{.underline}](https://github.com/openclover)

16. clover: how does it work? - Stack Overflow,
    > [[https://stackoverflow.com/questions/12723451/clover-how-does-it-work]{.underline}](https://stackoverflow.com/questions/12723451/clover-how-does-it-work)

17. An Empirical Comparison of Four Java-based Regression Test Selection
    > Techniques - Mountain Scholar,
    > [[https://mountainscholar.org/bitstreams/aa2d5c8b-5b7a-404a-bc91-0e51e11b9839/download]{.underline}](https://mountainscholar.org/bitstreams/aa2d5c8b-5b7a-404a-bc91-0e51e11b9839/download)

18. Unit Test Results and Per-Test Coverage - OpenClover,
    > [[https://openclover.org/doc/manual/latest/ant\--test-results-and-per-test-coverage.html]{.underline}](https://openclover.org/doc/manual/latest/ant--test-results-and-per-test-coverage.html)

19. Unit Test Results and Per-Test Coverage \| Clover Data Center 4.1,
    > [[https://ja.confluence.atlassian.com/spaces/CLOVER/pages/71600263/Unit+Test+Results+and+Per-Test+Coverage]{.underline}](https://ja.confluence.atlassian.com/spaces/CLOVER/pages/71600263/Unit+Test+Results+and+Per-Test+Coverage)

20. How to Provide Testwise Coverage for Test Impact Analysis (TIA) -
    > Teamscale Docs,
    > [[https://docs.teamscale.com/howto/providing-testwise-coverage/]{.underline}](https://docs.teamscale.com/howto/providing-testwise-coverage/)

21. Using OpenClover for per-test basis code coverage - Malintha
    > Amarasinghe,
    > [[https://malinthaprasan.medium.com/using-openclover-for-per-test-basis-code-coverage-3c9f2d41b9de]{.underline}](https://malinthaprasan.medium.com/using-openclover-for-per-test-basis-code-coverage-3c9f2d41b9de)
