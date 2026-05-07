# The `@PreDestroy` Phase: JSR-250 Cleanup Done Right

## Arriving at the Beginning of the End

Before we look at any code, I want you to pause with me and appreciate a shift in perspective that this phase asks of you. Throughout our entire conversation, we have been tracing a bean's journey from nothing into fullness. Construction gave it existence. Injection gave it collaborators. Initialization prepared it for work. Post-processing wrapped it in whatever cross-cutting behavior the application needed. The active phase, which we just finished exploring, was where the bean lived out its purpose, potentially for hours or days or months. All of this was a story of building up, of preparation leading to useful work.

Now we arrive at a phase where the direction reverses. The application is shutting down, and Spring is about to dismantle the bean it so carefully assembled. Before that dismantling happens, Spring gives the bean one last chance to do something important: clean up after itself. This is what the `@PreDestroy` callback is for. The name is worth reading carefully, because it tells you exactly when this callback fires. It runs *before* the bean is destroyed, meaning before Spring releases its reference, before any remaining resources are forcibly reclaimed, and before the memory is eventually collected. The bean is still alive, still fully functional, still able to do whatever it needs to do. This is its last moment of active existence, and the `@PreDestroy` method is the code that runs during that last moment.

There is something almost philosophical about this phase, and I want to name it explicitly because it shapes how you should think about what belongs here. During the init phases, the bean was preparing to take on responsibilities. During the active phase, the bean was discharging those responsibilities. Now, during the destroy phase, the bean must relinquish those responsibilities gracefully. Every external resource it opened needs to be closed. Every background thread it started needs to be stopped. Every registration it made with external systems needs to be undone. Every commitment it made to the outside world needs to be concluded. The `@PreDestroy` callback is where all of this winding down happens, and doing it well is what separates a bean that shuts down cleanly from one that leaks resources, leaves stale registrations behind, or causes cascading failures as the application shuts down.

The analogy that often helps people is thinking of a bean's lifecycle like a person's workday. The init phase is like arriving at work, taking off your coat, turning on your computer, opening your tools, and getting ready. The active phase is the work itself, which fills most of the day. The `@PreDestroy` phase is like the end of the day, when you save your files, close your applications, tidy up your workspace, and turn off the lights before leaving. Skipping this wind-down phase does not immediately cause problems; you can technically just walk away. But the next person who uses the space finds open files, running processes, lights left on, and it gradually becomes harder and harder to work there. In a long-running application, the same principle applies. Beans that do not clean up properly leave behind exactly the kind of detritus that slowly degrades the system, until one day something breaks and the cause is invisible among the accumulated mess.

Let me walk you through this phase through a progression of examples that grow from trivial to substantial, so you can build real intuition for what belongs in `@PreDestroy` and, just as importantly, how to write it well.

## Example 1: The Simplest Possible Cleanup

Let's start with a bean that does essentially nothing during its active phase but logs a message when it is about to be destroyed. This minimalism will let us focus entirely on the timing and the mechanics before we layer on realistic cleanup responsibilities.

```java
@Service
public class SimpleLoggingBean {

    @PostConstruct
    public void init() {
        // This runs at startup, during the initialization phase.
        // It is paired in our minds with the @PreDestroy method below,
        // which will run at shutdown, during the destruction phase.
        System.out.println("SimpleLoggingBean coming to life");
    }

    // The @PreDestroy annotation marks this method as the cleanup callback.
    // Spring will invoke it exactly once, when the application context is closing,
    // after the bean has finished its active phase but before Spring lets go of it.
    @PreDestroy
    public void cleanup() {
        // At this moment, the bean is still fully alive and functional.
        // We could still call any of its other methods if we wanted to.
        // This is our last opportunity to do anything meaningful with this bean.
        System.out.println("SimpleLoggingBean preparing to be destroyed");
    }
}
```

Trace through what happens when the application shuts down. The user sends a shutdown signal, Spring receives the signal and begins the orderly destruction of the application context. Before any bean's reference is released, Spring goes through every bean and looks for cleanup callbacks. For our bean, it finds the `@PreDestroy` annotation, invokes the `cleanup` method, and only after that method returns does Spring move on to the next stage of destruction. The sequence is as orderly as the initialization sequence was, just in reverse.

Notice the symmetry between `@PostConstruct` and `@PreDestroy`. The JSR-250 specification that defined both of these annotations was deliberately designed to be symmetrical. One runs once at the start of the bean's life, after construction is complete. The other runs once at the end of the bean's life, before destruction begins. The two callbacks form a natural pair, and you will often find that well-designed beans have matching `@PostConstruct` and `@PreDestroy` methods where one acquires resources and the other releases them. This pairing is a pattern I want you to keep noticing throughout the examples to come, because it is the foundation of responsible resource management in any managed-object framework.

There is a subtle detail about the method signature that mirrors what we discussed for `@PostConstruct`. The method must take no parameters and return void, because Spring has no way to decide what to pass in or what to do with a return value. The method can be private, package-private, protected, or public; modern practice tends toward public, but there is no technical requirement. The method name can be anything you like, because the annotation is what tells Spring to call it, not the name. Naming it `cleanup` or `shutdown` or `dispose` is conventional, but the framework does not enforce any particular name.

## Example 2: Closing a Database Connection

Let's move to a realistic and important use case. One of the most common reasons to write a `@PreDestroy` method is to close a database connection that the bean opened during initialization. A database connection is a precious external resource, and failing to close it properly is the kind of leak that can slowly exhaust your database's connection pool and eventually bring down your application or the database server.

```java
@Service
public class DatabaseClient {

    @Value("${db.url}")
    private String url;

    @Value("${db.user}")
    private String user;

    @Value("${db.password}")
    private String password;

    // The connection object lives as a field throughout the bean's active phase.
    // It was opened during initialization and will be closed during destruction.
    // This pairing is one of the most classic examples of proper resource management.
    private Connection connection;

    @PostConstruct
    public void connect() throws SQLException {
        // During initialization, we open the connection. This corresponds to
        // the "acquire" half of the acquire-release pattern for external resources.
        this.connection = DriverManager.getConnection(url, user, password);
        System.out.println("Database connection opened");
    }

    public void executeQuery(String sql) throws SQLException {
        // During the active phase, we use the connection to do real work.
        // This is what all the initialization and eventual cleanup is in service of.
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @PreDestroy
    public void disconnect() {
        // During destruction, we close the connection. This corresponds to
        // the "release" half of the acquire-release pattern. If we forgot this
        // method, the connection would leak, staying open on the database side
        // long after our application thought it was done with it.
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            // Cleanup code should be resilient. Throwing from @PreDestroy would
            // disrupt the orderly shutdown of other beans, which can cause cascading
            // cleanup failures. We log the error and continue, accepting that we did
            // our best and that the JVM exit will eventually reclaim any remaining state.
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}
```

I want to slow down on several details in this code because they illustrate principles that apply to almost every `@PreDestroy` method you will ever write. The first detail is the null check and the `isClosed` check inside the cleanup method. These checks protect against edge cases where the bean might be destroyed without having been fully initialized, which can happen if initialization failed partway through. A `@PreDestroy` method should be defensive, assuming that the state it expects might not actually be there. Writing cleanup code that works even when the bean is in a partially-initialized state is one of the marks of thoughtful framework code, and it is a habit worth developing.

The second detail is the exception handling. Notice that we catch the `SQLException` from `connection.close()` rather than letting it propagate. This is a deliberate choice, and understanding why it is the right choice will save you from a whole class of shutdown bugs. When Spring is shutting down the application context, it goes through every bean and calls its destroy callbacks one by one. If any callback throws, Spring logs the error but continues calling other callbacks, because it wants to give every bean a chance to clean up even if some of them fail. However, an exception from a destroy callback can still cause problems: it might prevent the bean's own cleanup from completing, it adds noise to the shutdown logs, and in some cases it can interfere with downstream tools that monitor shutdown sequences. The defensive pattern is to catch exceptions inside your cleanup method, log them if needed, and return normally. This keeps the shutdown flow orderly and ensures that every bean gets a clean chance to do its own cleanup.

The third detail is the pairing itself. Notice how `@PostConstruct` opens the connection and `@PreDestroy` closes it. These two methods are a matched pair, each taking responsibility for one end of the connection's lifetime. This pairing is more than a convention; it is a design principle that you should internalize. Whenever you write a `@PostConstruct` method that acquires some kind of external resource, ask yourself whether the destroy phase needs a corresponding method to release it. The answer is almost always yes for anything involving external state. Connections, files, threads, network sockets, registrations with external systems, and subscriptions to event sources all fall into this category. Making the pairing explicit in your code, rather than leaving cleanup as an afterthought, is what keeps your application from leaking resources over its lifetime.

There is a question worth sitting with here. Why did we not just use Java's try-with-resources for the connection? This is a perfectly reasonable question, and the answer reveals something important about the scope of resource management in Spring beans. Try-with-resources is ideal for resources whose lifetime matches a single method or block of code. The connection opens, you use it within the block, and it closes when the block exits. But the connection in our bean is different. It needs to live for the entire active phase of the bean, which might span days or weeks. Try-with-resources cannot express this longer lifetime, because its scope is a single block. For resources that live longer than a single method call, you need an explicit acquire-and-release pattern that matches the bean's lifecycle, and `@PostConstruct` paired with `@PreDestroy` is exactly that pattern. Recognizing when try-with-resources is sufficient and when you need the lifecycle-aware pattern is one of the small skills that separates experienced Spring developers from those still learning the ropes.

## Example 3: Stopping a Background Thread

Let's look at a use case that is especially important because getting it wrong causes some of the most frustrating bugs in real applications. Many beans start background threads or scheduled tasks during initialization, and these threads need to be stopped properly during destruction. A background thread that continues running after the application context has closed is called a "leaked thread," and leaked threads can prevent the JVM from exiting cleanly, cause weird behavior in tests, and in the worst cases continue to hold onto resources that should have been released.

```java
@Service
public class PeriodicCleaner {

    private final CleanupRepository repository;
    private ScheduledExecutorService scheduler;

    public PeriodicCleaner(CleanupRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void startCleaning() {
        // We create a scheduler that owns the cleanup task. The scheduler runs
        // on its own thread, separate from whatever thread called us here.
        // This thread will continue to run as long as the scheduler is active,
        // which is why we will need to shut it down explicitly during destruction.
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            // Giving the thread a descriptive name makes shutdown problems easier
            // to diagnose, because any leaked threads show up in thread dumps
            // with names you can recognize rather than as generic "Thread-5".
            Thread thread = new Thread(runnable, "periodic-cleaner");
            // Daemon threads do not prevent JVM exit, which is a backup safety net
            // in case our @PreDestroy fails to stop the scheduler for some reason.
            // Relying on daemon status alone is not sufficient, because daemon threads
            // can still hold resources that need explicit release, but it is a useful
            // additional protection against the absolute worst case of a hanging JVM.
            thread.setDaemon(true);
            return thread;
        });

        // Schedule the cleanup task to run every five minutes.
        scheduler.scheduleAtFixedRate(this::cleanupExpiredRecords, 5, 5, TimeUnit.MINUTES);
        System.out.println("PeriodicCleaner started");
    }

    private void cleanupExpiredRecords() {
        // The background work itself, running on the scheduler's thread.
        repository.deleteExpired();
    }

    @PreDestroy
    public void stopCleaning() {
        // This is the cleanup code that pairs with startCleaning. We need to
        // shut down the scheduler so that the background thread stops running.
        // How we shut it down matters, and I want to walk through the choice carefully.

        if (scheduler == null) {
            // The defensive check, in case initialization failed and the scheduler
            // was never created. Without this, we would get a NullPointerException
            // during destruction of a partially-initialized bean.
            return;
        }

        // shutdown() tells the scheduler to stop accepting new tasks and to let
        // any currently running task complete. This is usually what we want,
        // because it avoids interrupting work that is in the middle of something important.
        scheduler.shutdown();

        try {
            // We wait a bounded amount of time for the current task to finish.
            // This is a courtesy to the task, giving it a chance to complete gracefully.
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                // If the task did not finish in time, we resort to shutdownNow(),
                // which interrupts running tasks and returns a list of queued tasks
                // that never ran. This is the stronger form of shutdown, appropriate
                // when graceful completion takes too long.
                System.out.println("Task did not complete in time, forcing shutdown");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            // If our own thread is interrupted while waiting, we should propagate
            // the interruption by forcing shutdown and re-interrupting the thread.
            // This is the standard pattern for handling InterruptedException in
            // shutdown code, and it preserves the intent of the interruption.
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("PeriodicCleaner stopped");
    }
}
```

There is a lot to absorb in this example, and I want to walk through the reasoning behind several decisions so that the patterns become memorable rather than just memorized. The first thing to notice is the two-stage shutdown, starting with `shutdown()` and falling back to `shutdownNow()` if graceful termination takes too long. The reason for this two-stage approach is that different kinds of tasks have different tolerances for interruption. A task that is halfway through writing a batch of records to the database should probably be allowed to finish, because interrupting it mid-write could leave partial data. A task that is in the middle of a long-running computation that does not affect external state can be interrupted safely. By first requesting graceful shutdown and then escalating to forced shutdown after a timeout, we accommodate both kinds of tasks. The timeout itself is a judgment call; ten seconds is reasonable for most periodic tasks, but the right value depends on what your tasks actually do.

The second thing to notice is the handling of `InterruptedException`. If our own thread is interrupted while waiting for the scheduler to terminate, we need to do two things: force the scheduler to shut down immediately, and re-interrupt our own thread to preserve the interruption signal. The reason to re-interrupt is subtle but important. The interruption was sent by someone, probably the shutdown coordinator, and that party is relying on the interruption being honored. Catching `InterruptedException` silently swallows that signal, which can cause the shutdown process to hang or behave unpredictably. Re-interrupting the thread, even after you have handled the interruption, is the idiomatic way to say "I noticed the interruption and I am acting on it."

There is a third thing worth appreciating about this code, which is how it models a broader pattern. Whenever your bean starts something during initialization that has its own lifecycle, such as a thread, a scheduler, a connection, or a subscription, you have taken on a responsibility to end that thing during destruction. The pattern is always the same: capture a reference to the thing in a field during initialization, write a `@PreDestroy` method that gracefully terminates it, handle the termination carefully to avoid leaving the thing in an inconsistent state, and defend against partial initialization. Once you see this pattern clearly, many different kinds of resources fall into the same mental category, and the code to manage each of them looks remarkably similar. This kind of pattern recognition is what makes lifecycle code feel manageable rather than overwhelming.

## Example 4: Unregistering from an External System

Let's look at a use case that shows up often when you integrate your application with external services. Suppose your bean registers itself with an external event bus, message broker, service discovery system, or monitoring platform during initialization. This registration creates a bond between your bean and the external system, and the external system continues to hold a reference to your bean as long as the registration is active. When your application shuts down, you need to unregister so that the external system knows to stop sending traffic your way.

```java
@Service
public class ServiceDiscoveryClient {

    private final DiscoveryRegistry registry;

    @Value("${service.name}")
    private String serviceName;

    @Value("${service.host}")
    private String serviceHost;

    @Value("${service.port}")
    private int servicePort;

    // A token returned by the registration call. We will need this token
    // later to deregister. This is a common pattern in external systems:
    // registration returns a handle that you use to undo the registration.
    private RegistrationToken registrationToken;

    public ServiceDiscoveryClient(DiscoveryRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void register() {
        // During initialization, we tell the discovery system that our service
        // is available and reachable at a particular host and port. After this
        // call returns, other services that query the discovery system will
        // find us and may start sending requests our way.
        this.registrationToken = registry.register(serviceName, serviceHost, servicePort);
        System.out.println("Registered " + serviceName + " with discovery service");
    }

    @PreDestroy
    public void deregister() {
        // During destruction, we tell the discovery system that our service
        // is going away and should no longer be advertised. This is important
        // because if we simply stopped without deregistering, other services
        // might continue to send requests to us for some time, and those
        // requests would fail with connection errors rather than being routed
        // to healthy instances of our service.

        if (registrationToken == null) {
            // The defensive check in case registration never happened,
            // for example if initialization failed partway through.
            return;
        }

        try {
            registry.deregister(registrationToken);
            System.out.println("Deregistered " + serviceName + " from discovery service");
        } catch (Exception e) {
            // As with the database example, we catch exceptions rather than
            // letting them propagate. The discovery system might be down, or
            // the network might be flaky, or any number of other transient
            // problems might prevent a clean deregistration. We log the failure
            // and accept that our service will be marked unhealthy by the
            // discovery system's own health checks after a short delay.
            System.err.println("Error deregistering from discovery: " + e.getMessage());
        }
    }
}
```

I want to draw your attention to a principle illustrated here that applies to almost every kind of external registration. The external system does not know when your application is shutting down unless you tell it. It continues to hold its registration and to route traffic according to that registration until something explicitly tells it otherwise. If your bean dies without telling the external system, the external system will eventually figure out through its own health checks that your bean is gone, but during the gap between your shutdown and its realization, traffic continues to flow to your dead instance. This gap is usually seconds, but sometimes minutes, and during the gap requests fail, users see errors, and downstream systems register failures that cascade.

The `@PreDestroy` method is your opportunity to close this gap deliberately. By actively deregistering before shutting down, you tell the external system to stop sending traffic your way immediately, so that the traffic shifts to healthy instances without any gap. This is what graceful shutdown looks like in a distributed system, and it is genuinely important for any application that participates in service meshes, load balancers, or event-driven architectures.

Here is a question that will deepen your understanding. Suppose the discovery system itself has shut down before your application did, perhaps because they are both being shut down together. What happens when your `@PreDestroy` tries to deregister? Trace through the possibilities. The deregister call would fail, probably with a connection error. Our exception handler catches the error and logs it. The application continues shutting down. The discovery system eventually realizes that we are not responding to health checks and marks us unhealthy through its own mechanisms. This degraded path is not ideal, but it is safe, because we did not let the error propagate and disrupt the rest of the shutdown. This is why the defensive exception handling matters: it makes your shutdown robust to failures in the external systems you depend on.

## Example 5: Flushing Buffered Data Before Shutdown

One last use case deserves attention because it reveals a dimension of `@PreDestroy` that is easy to miss. Suppose your bean accumulates data during the active phase and periodically flushes the data to some external destination, such as writing log events to a file, sending metrics to a monitoring system, or persisting state to a database. The accumulation is usually done in batches for efficiency, which means there is almost always some unflushed data in memory at any given moment. If the application shuts down without flushing the final batch, that data is lost forever.

```java
@Service
public class BufferedEventLogger {

    private final ExternalLoggingService loggingService;

    // A buffer that accumulates events during the active phase.
    // Using a ConcurrentLinkedQueue because events are added from many threads
    // and occasionally drained by our periodic flush task.
    private final Queue<Event> buffer = new ConcurrentLinkedQueue<>();

    private ScheduledExecutorService scheduler;

    public BufferedEventLogger(ExternalLoggingService loggingService) {
        this.loggingService = loggingService;
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        // Periodic flush every five seconds, which is the routine path.
        scheduler.scheduleAtFixedRate(this::flush, 5, 5, TimeUnit.SECONDS);
    }

    public void logEvent(Event event) {
        // During the active phase, events are added to the buffer rapidly.
        // The buffer grows and shrinks as events come in and flushes happen.
        buffer.offer(event);
    }

    private void flush() {
        // The routine flush, called every five seconds by the scheduler.
        // We poll events out of the buffer and send them to the external service.
        Event event;
        List<Event> batch = new ArrayList<>();
        while ((event = buffer.poll()) != null) {
            batch.add(event);
        }
        if (!batch.isEmpty()) {
            loggingService.sendBatch(batch);
        }
    }

    @PreDestroy
    public void shutdown() {
        // The destruction-time cleanup has two responsibilities. First, it must
        // stop the scheduler so no new flushes are triggered. Second, and this
        // is the subtle part, it must perform one final flush to ensure that
        // any events still in the buffer at shutdown time actually reach the
        // external service. Without this final flush, any events that arrived
        // since the last periodic flush would be lost forever.

        // Stop the scheduler first, so that no new flushes start while we are
        // doing our final flush. Otherwise we might race with a scheduled flush
        // and end up with events double-sent or lost.
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Now perform the final flush, with the guarantee that no other flush
        // is running concurrently. Any event that was logged up to this point
        // will be included in this final batch.
        System.out.println("Performing final flush on shutdown");
        flush();

        System.out.println("BufferedEventLogger shut down, all events flushed");
    }
}
```

This example illustrates a pattern that is genuinely important in production systems. Any bean that buffers data for efficiency has an implicit contract with the outside world: the data that users logged will eventually be persisted somewhere. If your shutdown breaks that contract by losing the final buffer, users see missing data, logs have gaps, metrics show zeros where they should show real numbers, and debugging after the fact becomes much harder. The final flush in `@PreDestroy` is what honors the contract, ensuring that even data logged one millisecond before shutdown makes it to its destination.

There is an ordering concern in this example that is worth making explicit. Notice that we stop the scheduler first and only then perform the final flush. Why this order rather than the reverse? If we had flushed first and then stopped the scheduler, the scheduler might have fired one more flush between our call to `flush()` and our call to `scheduler.shutdown()`. That additional flush would run concurrently with whatever comes next in our `@PreDestroy`, creating the possibility of race conditions and double-processing. By stopping the scheduler first, we guarantee that our final flush is the absolute last flush that will happen, with no competing flushes from the scheduler. This kind of ordering thinking is important for any cleanup code that involves stopping ongoing activity and performing final actions. The pattern is: first stop new activity from starting, then complete any final work that needs to happen with no concurrency.

## The Comparison to Destructors in Other Languages

I want to take a brief detour to place `@PreDestroy` in a broader context, because seeing how other languages handle the same problem will deepen your appreciation for what Spring offers. Many languages have some concept of "run code when an object is destroyed." C++ has destructors, which run automatically when an object goes out of scope. Python has `__del__` methods, which run when the garbage collector reclaims the object. Java itself has `finalize`, which the garbage collector calls before reclaiming an object. Each of these mechanisms has its own quirks and limitations, and Spring's `@PreDestroy` addresses a specific gap that none of them fill adequately.

C++ destructors are the gold standard of the bunch, because they fire deterministically when the object's scope ends. But C++ objects are typically managed by explicit scope or by smart pointers with well-defined lifetimes, whereas Java objects live until the garbage collector decides to reclaim them, which can be seconds or minutes or even never. Python's `__del__` has the same problem: you cannot rely on it firing at any particular time, and in some cases it does not fire at all. Java's `finalize` is the same story with even more caveats, and it has been so problematic that it was deprecated in recent Java versions.

What all of these mechanisms share is a lack of determinism. They fire when the object is collected, not when the object stops being useful. Spring's `@PreDestroy` fires deterministically, at a specific moment controlled by the application context, which is exactly when you want cleanup to happen. This determinism is possible because Spring manages the bean's lifecycle explicitly rather than leaving it to the garbage collector. When the application context closes, Spring walks through every managed bean and calls its cleanup callbacks in a controlled order, and only after those calls complete does Spring release its references and allow the beans to eventually be garbage collected. This is why `@PreDestroy` can be relied upon in a way that `finalize` never could be, and it is one of the quiet benefits of using a dependency injection framework for resource management.

There is a subtle point embedded here about why this matters practically. Your `@PreDestroy` method runs at a predictable time, during a predictable phase of shutdown, with the rest of your application still in a predictable state. This means your cleanup code can rely on other beans still being alive, on the logging system still working, on threads still being in recognizable states. Contrast this with `finalize`, which runs at an unpredictable time on a dedicated finalizer thread, with no guarantees about what else is still alive. Code written for `finalize` has to be incredibly defensive because so little can be assumed. Code written for `@PreDestroy` can be ordinary, because the framework provides predictability. This is a real quality-of-life difference, and it is one of the reasons that Spring beans are a better tool for resource management than plain Java objects with finalizers.

## What Should Not Go in `@PreDestroy`

A balanced understanding of this phase requires me to discuss not only what belongs in a `@PreDestroy` method but also what does not. There are several temptations that people commonly give in to when writing cleanup code, and most of them lead to problems that are not obvious until you have seen them go wrong.

The first temptation is doing long-running work. A `@PreDestroy` method that takes thirty seconds to complete will extend your shutdown time by thirty seconds for every instance of the bean, and in an application with many beans the cumulative delay can become significant. Worse, some deployment systems set timeouts on shutdown and will forcibly kill your application if it takes too long, which means your long-running cleanup might not actually complete. Keep `@PreDestroy` methods fast. If you need to do something slow, consider whether it really needs to happen during shutdown or whether it could happen asynchronously during the active phase.

The second temptation is making new commitments to external systems. A cleanup method is a bad place to start new work, subscribe to new services, or acquire new resources. The application is going away, and anything you start now will either not complete or will need to be cleaned up in a way that is awkward to express. If you catch yourself writing code in `@PreDestroy` that starts something new rather than ending something old, step back and reconsider the design.

The third temptation is throwing exceptions to signal failure. As I discussed earlier with the database example, exceptions from destroy callbacks disrupt the orderly shutdown of other beans. Catch exceptions inside the method, log them if they are worth logging, and return normally. This is one of the few places in Java where broad exception handling is actually the right choice, because the priority is completing the shutdown rather than signaling specific errors.

The fourth temptation is relying on the order of destruction. Spring destroys beans in reverse dependency order, so a bean is destroyed after everything that depends on it has already been destroyed. This generally means your `@PreDestroy` can safely call methods on its dependencies, because those dependencies are still alive. But relying on more specific ordering is fragile, because the exact order depends on implementation details that can change. If your cleanup depends on specific ordering beyond the basic dependency rule, find a way to express the dependency explicitly rather than hoping the order works out.

## A Closing Reflection on What This Phase Really Represents

Let me step back with you one final time and name what `@PreDestroy` really is in the architecture of the bean lifecycle. Every phase we have studied has had its own character, its own timing guarantees, and its own role in the bean's journey. Initialization was about preparation, and it ran once at startup. The active phase was about work, and it ran for the bulk of the application's lifetime. Destruction is about winding down, and it runs once at shutdown, in the narrow window between the active phase ending and the bean being actually disposed of.

The `@PreDestroy` callback exists because the framework cannot know, on your behalf, what your bean needs to clean up. Spring can release its own references to the bean, but it has no way to know that your bean holds a database connection, or started a background thread, or registered with an external service. Only you know what commitments your bean made to the outside world, and only you can know how to unmake those commitments gracefully. The callback is the framework's invitation for you to express that knowledge in code, at exactly the right moment for the code to matter.

This framing might feel overly philosophical, but I think it captures something true about good lifecycle design. Frameworks manage what they can manage, which is typically the objects and references that exist inside the framework's own control. Users of the framework manage what the framework cannot see, which is typically the external state that the objects interact with. The lifecycle callbacks are the protocol by which the framework and the user coordinate, with the framework providing timing guarantees and the user providing domain-specific cleanup logic. When both sides of this protocol do their jobs well, resource management becomes almost automatic, and applications can be started and stopped cleanly regardless of how complex their internal state is. When either side fails, the result is leaks, race conditions, and the slow degradation of long-running systems.

Here is the thought I most want you to carry forward from this section. Writing good `@PreDestroy` methods is one of the most underappreciated skills in Spring development. Most developers pour effort into initialization, because that is where bugs are visible during development. Destruction gets less attention because its bugs often manifest only in production, often after long uptimes, and often in ways that are hard to connect back to the cleanup code that should have prevented them. Developing the habit of thinking about cleanup alongside initialization, and of writing matched pairs of `@PostConstruct` and `@PreDestroy` methods that explicitly manage the lifetimes of the resources your bean holds, is what separates code that merely works during development from code that operates reliably for years in production. You now have the tools and the mental model to do this well, and that understanding will pay dividends for the rest of your career working with managed-object frameworks, not just Spring.

There is a thought exercise worth ending on that connects this section to the ones before it. Every resource you saw acquired in any of our examples across this conversation, whether a connection, a thread, a subscription, a registration, or a buffered data source, should have a corresponding release action in a `@PreDestroy` somewhere. Take a mental tour back through the examples we built together. For each one, ask yourself whether the bean we designed would leak resources if the application shut down. In some cases, the answer is no because the bean did not acquire any external resources. In other cases, the answer is yes, and the bean we showed would benefit from an explicit cleanup method. Going through this exercise makes the acquire-release pattern concrete across the whole landscape of Spring bean design, and it gives you a habit of thinking about cleanup that will serve you well long after any specific example has faded from memory. The next time you write a `@PostConstruct` method, your first question should not be "what should this method do," but rather "what will need to be cleaned up, and where will I put the cleanup code." That question, asked consistently, is the foundation of writing Spring applications that are as robust in shutdown as they are in operation.