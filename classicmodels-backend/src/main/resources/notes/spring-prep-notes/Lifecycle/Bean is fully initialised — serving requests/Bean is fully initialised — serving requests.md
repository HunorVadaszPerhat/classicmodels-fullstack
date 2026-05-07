# The Active Phase: When the Bean Is Fully Initialized and Serving Requests

## What It Really Means for a Bean to Be "Ready"

Before we look at any code, I want to spend some real time with a phrase that sounds simple but that carries more meaning than it first appears. Your log output from the very first question showed a line that read "Bean in use — ACTIVE phase," and in the diagram the corresponding stage was called "Bean is fully initialised — serving requests." At first glance this might seem like a trivial milestone, almost a formality between the init callbacks and the eventual destroy callbacks. But this is actually the phase that the entire lifecycle has been preparing the bean for, and understanding what happens during this phase, and what the framework guarantees about the bean during it, is one of the most practically valuable things you can take away from studying Spring's lifecycle.

Think about everything the lifecycle has done for the bean up to this point. Construction gave the bean existence. Injection gave it its collaborators. Aware callbacks gave it access to the framework. Pre-init post-processing gave external code a chance to validate or transform it. Init callbacks let the bean run its own setup. Post-init post-processing gave external code a chance to wrap it in proxies that add cross-cutting behavior. Every one of these phases happened exactly once, at startup, and every one of them was preparation. The active phase is where all that preparation finally gets used. It is where the bean does the actual work it was created to do, and it is also, in almost every real application, the phase that lasts by far the longest. An init callback might run for milliseconds, but the active phase runs for as long as the application is up, which might be hours, days, or months. Everything you put effort into getting right during initialization exists to serve this one long phase.

There is another thing worth naming about this phase. It is the only phase that Spring does not actively manage. Every other phase in the lifecycle involves Spring calling methods on your bean: the constructor, the setters, the Aware callbacks, the init methods, the destroy methods. During the active phase, Spring mostly steps out of the way. Other beans call into your bean, the application routes requests to your bean, and your bean does its work without Spring intervening on each call. Spring is still present in the background, because proxies installed during post-processing are still intercepting calls, and the application context is still holding a reference to your bean so that it can be found when needed. But the frame-by-frame choreography of the init phases is over. Your bean is on its own now, doing what it was built to do.

Let me walk you through what this means in practice, through a series of examples that will make the active phase concrete and give you real intuition for what your code can rely on during it.

## Example 1: The Simplest Possible Active Bean

Let's start with the most basic version of what an active bean looks like. A bean becomes active the moment all of its initialization is complete, and from that moment on, other beans can inject it and call its methods freely. There is no ceremony that marks the transition; it simply happens when the last init step completes and the post-init post-processors have had their say.

```java
@Service
public class GreetingService {

    @Value("${greeting.default:Hello}")
    private String defaultGreeting;

    @PostConstruct
    public void init() {
        // This runs during initialization, before the bean is active.
        // Once this method returns and any post-processors finish, the
        // bean transitions into the active phase.
        System.out.println("GreetingService initializing with default: " + defaultGreeting);
    }

    // This is a business method. It runs during the active phase,
    // which means it runs after all initialization has completed.
    // Every call to this method happens in the active phase, and this
    // is what the bean spends the vast majority of its life doing.
    public String greet(String name) {
        // At the moment this method runs, we can rely on every single
        // field being fully initialized. The defaultGreeting was populated
        // during injection, and if we had done any further setup in @PostConstruct,
        // that setup is also complete. No defensive null checks needed.
        return defaultGreeting + ", " + name;
    }
}
```

Another bean uses this service during its own active phase.

```java
@RestController
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        // Constructor injection gives us the service.
        // At the moment this constructor runs, the service is guaranteed
        // to be fully active, because Spring does not inject a bean into
        // another bean until the injected bean has completed all its init.
        this.greetingService = greetingService;
    }

    @GetMapping("/greet/{name}")
    public String handleGreeting(@PathVariable String name) {
        // This method runs during the controller's active phase, and when
        // it calls into the service, the service is also in its active phase.
        // Both beans are fully initialized and working together to handle a request.
        return greetingService.greet(name);
    }
}
```

Trace through what happens when someone hits the `/greet/Alice` endpoint in a running application. The request arrives at the web server, which dispatches it to the controller's method. The controller calls the service's method. The service returns a greeting. The controller returns the string. The web server sends the response. Notice that Spring does not appear anywhere in this flow. The framework set everything up beforehand, but once the active phase began, the beans simply call each other and Spring watches from the sidelines. This is what makes the active phase feel so different from the init phases. Initialization is dense with framework involvement; active work is almost free of it.

I want you to appreciate something subtle about the guarantee we leaned on when the controller called the service. We assumed that by the time the controller's method runs, the service is fully active. How does Spring actually enforce this guarantee? The answer is that Spring's dependency resolution will not inject a bean into another bean until the injected bean has completed all its initialization, including post-processing. This means that when the controller was being constructed and had the service injected, the service had already gone through every phase we have discussed and was sitting in the active phase, ready to serve requests. Spring builds the dependency graph from the bottom up, finishing each bean's initialization completely before using it as a dependency for other beans. This is the mechanism that makes the active phase so reliable: by the time any bean is active, every bean it depends on is also active.

## Example 2: Concurrent Access and Thread Safety

The active phase has one characteristic that sharply distinguishes it from every other phase. During initialization, Spring calls your bean's methods in a specific order from a specific thread, one at a time, with full control over the sequence. During the active phase, your bean can be called from any number of threads simultaneously, in any order, with no coordination from Spring. If your bean holds any state that can change, and if multiple threads can interact with that state concurrently, you are now responsible for thread safety.

Let me show you a bean that handles concurrent access correctly, along with one that does not, so you can see the difference clearly.

```java
@Service
public class UnsafeCounter {

    // A plain int field that will be accessed from multiple threads.
    // This field is mutable state, and it will be modified concurrently
    // during the active phase. This is where thread-safety concerns begin.
    private int count = 0;

    public void increment() {
        // This looks like a single operation, but it actually consists of
        // three steps: read count, add one, write count back. Between the
        // read and the write, another thread might also be reading and writing,
        // and both threads might end up writing the same incremented value.
        // The result is that increments get lost, and the counter falls behind.
        count++;
    }

    public int getCount() {
        // Reading an int is atomic in Java, so this is not corrupted by
        // concurrent writes, but it might return a stale value that does
        // not reflect writes from other threads that have not yet become visible.
        return count;
    }
}
```

The same bean written with proper thread safety looks like this.

```java
@Service
public class SafeCounter {

    // AtomicInteger provides thread-safe operations on a single int value.
    // Its incrementAndGet method performs the read-modify-write sequence
    // atomically, so concurrent calls cannot step on each other.
    private final AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        // incrementAndGet is a single atomic operation, so no matter how
        // many threads call this method concurrently, every increment is
        // counted exactly once. This is the behavior you want for any counter
        // that lives through the active phase of a multi-threaded application.
        count.incrementAndGet();
    }

    public int getCount() {
        // AtomicInteger also guarantees visibility, meaning any increment
        // performed by any thread is immediately visible to every other thread.
        // This is stronger than a plain int field provides.
        return count.get();
    }
}
```

I want to pause here because the thread-safety question is one of the most important things to internalize about the active phase. Spring creates singleton beans by default, which means a single instance of each bean is shared across the entire application. Every request handler, every scheduled task, every background thread, and every incoming connection ends up calling into the same instance. If that instance has mutable state, every concurrent path that touches the state is a potential source of bugs that will be nearly impossible to reproduce in development because they require specific timing of concurrent threads.

The rule of thumb that saves you from most of these problems is to design your beans to be stateless during the active phase. A stateless bean has no mutable state of its own; it only holds references to other beans and to immutable configuration values. Every method on a stateless bean operates purely on its arguments and returns a result, possibly delegating to other beans along the way. If you follow this pattern rigorously, thread safety is a non-issue because there is nothing to be concurrently modified. When you do need mutable state, reach for thread-safe primitives like `AtomicInteger`, `ConcurrentHashMap`, and `CopyOnWriteArrayList`, or wrap access in explicit synchronization. The point is to be conscious about the choice. Silently shared mutable state during the active phase is one of the most common sources of bugs in real Spring applications, and the fact that Spring does not warn you about it means the responsibility falls entirely on you.

Here is a thought exercise worth running through in your head. Suppose you wrote a service that holds a list of recently-seen user IDs and adds to the list whenever a user logs in. You declare the field as `private List<String> recentUsers = new ArrayList<>()` and add an `add` method. What goes wrong in the active phase? Trace through the possibilities. Multiple users might log in concurrently, causing multiple threads to call `add` at the same time. `ArrayList` is not thread-safe, so concurrent modifications can corrupt its internal structure, throwing `ConcurrentModificationException`, losing entries, or in the worst case producing a list that appears to contain garbage data. The fix is either to use `CopyOnWriteArrayList`, which is safe for concurrent addition, or to synchronize access explicitly, or, best of all, to not hold the state in the bean at all and instead persist it to a database where concurrency is handled for you. Recognizing these situations early is a skill that develops with experience, but the habit of asking "is this field accessed concurrently" whenever you add a field to a singleton bean will serve you well for the rest of your career.

## Example 3: Beans That Cache Data During the Active Phase

A common and legitimate use of mutable state during the active phase is caching. A service might maintain an in-memory cache of expensive lookups, refreshing entries as needed. This is genuinely useful, and done correctly it is safe, but done carelessly it combines all the thread-safety concerns we just discussed with additional questions about cache invalidation and consistency.

```java
@Service
public class ProductCatalog {

    private final ProductRepository repository;

    // A concurrent map that holds cached products, keyed by their ID.
    // ConcurrentHashMap is designed for high-concurrency access, so reads
    // and writes can happen from many threads simultaneously without
    // corrupting the map's internal structure.
    private final Map<Long, Product> cache = new ConcurrentHashMap<>();

    public ProductCatalog(ProductRepository repository) {
        this.repository = repository;
    }

    public Product findById(long id) {
        // computeIfAbsent is the key operation here. It atomically checks
        // whether the key exists in the map, and if not, calls the provided
        // function to compute the value and stores the result. Critically,
        // it guarantees that for any given key, the function runs at most once
        // even if many threads call computeIfAbsent with the same key concurrently.
        // This prevents duplicate database lookups for the same product.
        return cache.computeIfAbsent(id, repository::findById);
    }

    public void invalidate(long id) {
        // Removing a cached entry is also safe because ConcurrentHashMap
        // handles concurrent removal correctly. After this call returns,
        // the next findById for this id will perform a fresh lookup.
        cache.remove(id);
    }
}
```

There is a design decision embedded in this example that is worth making explicit. We are holding cached state across the entire active phase of the bean, which in a typical Spring application means the entire lifetime of the JVM. This is fine for data that does not change often, but it has consequences. If a product gets updated in the database but our cache is not invalidated, our cache will serve stale data indefinitely. For many use cases this is acceptable; for others, it is a serious correctness problem. The question of how to keep a cache consistent with its underlying source of truth is one of the classic hard problems in computing, and there is no universal answer. In real applications, you typically combine approaches: time-based expiration that limits how stale data can be, explicit invalidation when writes happen, and, for high-stakes data, simply avoiding in-bean caching in favor of a dedicated cache service like Redis where invalidation is coordinated across all instances of the application.

The general principle to carry away from this is that the active phase is long, and state that you create during it tends to live for the entire phase. Every mutable field on a singleton bean is a piece of state with a potentially very long lifetime, and the correctness of that state over that lifetime is your responsibility. Being thoughtful about what state you create, why you need it, and how you will keep it consistent is one of the quiet arts of writing production-quality Spring code.

## Example 4: Beans That Interact with Scheduled Tasks

Some beans spawn background activity during initialization that continues throughout the active phase. A scheduled task that runs every minute, a background thread that processes a queue, or a timer that periodically refreshes cached data all fit this pattern. The interaction between the background activity and the bean's normal business methods is a concurrency concern that the active phase forces us to handle carefully.

```java
@Service
public class MetricsAggregator {

    // A thread-safe map that holds aggregated metric values.
    // Multiple threads can update metrics concurrently, and a background
    // task will periodically flush the accumulated values to an external system.
    private final Map<String, LongAdder> metrics = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void startFlushing() {
        // We create a scheduler that will run our flush task periodically.
        // This starts during initialization but the task itself runs during
        // the active phase, concurrently with business methods that record metrics.
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::flushMetrics, 10, 10, TimeUnit.SECONDS);
    }

    public void record(String metricName, long value) {
        // This method is called from business code during the active phase.
        // It can run concurrently with other calls to record and with the
        // flush task that runs on the scheduler's thread. Our use of LongAdder
        // and ConcurrentHashMap ensures that all of this concurrency is safe.
        metrics.computeIfAbsent(metricName, key -> new LongAdder()).add(value);
    }

    private void flushMetrics() {
        // This method runs on the scheduler's thread, concurrently with
        // whatever business threads are calling record. We iterate over the
        // map and send values to the external system. The iteration is safe
        // because ConcurrentHashMap allows concurrent reads and modifications,
        // though we might see entries added during iteration, which is fine
        // for our purposes because we will catch them on the next flush.
        metrics.forEach((name, value) -> {
            long sum = value.sumThenReset();
            if (sum > 0) {
                sendToExternalSystem(name, sum);
            }
        });
    }

    private void sendToExternalSystem(String name, long value) {
        System.out.println("Flushing metric " + name + " = " + value);
    }

    @PreDestroy
    public void stop() {
        // When the active phase ends and destruction begins, we stop the scheduler.
        // We will explore this in detail when we cover the destroy phase, but notice
        // that the existence of the background task creates a responsibility to
        // clean it up, which is a pairing we have seen before.
        scheduler.shutdown();
    }
}
```

Notice how the active phase for this bean involves two completely different kinds of activity running at the same time. Business code calls `record` on whatever thread happens to be handling a request. The scheduler runs `flushMetrics` on its own dedicated thread every ten seconds. Both activities happen during the active phase, and both must coexist without corrupting the shared state. The use of `ConcurrentHashMap` and `LongAdder` is not decorative; it is what makes this coexistence possible without explicit locking. If we had used plain `HashMap` and `long`, the concurrent access would eventually corrupt the map or lose counter updates in subtle ways that would be nearly impossible to diagnose from a production crash report.

There is a broader principle at work here that applies to any bean with internal background activity. Once the active phase begins, the bean is effectively running in its own private multi-threaded environment, even if the outside world only calls into it from a single thread. Any state shared between the business methods and the background tasks needs to be safe for the concurrent access that arises. Designing this correctly is a skill in its own right, and it is one of the reasons that experienced Spring developers tend to push background work out of beans and into dedicated infrastructure like message queues, database-backed job systems, or frameworks like Spring's own scheduling support with proper transactional boundaries. The more you can avoid rolling your own concurrency inside a bean, the less exposure you have to the kinds of bugs that make production systems mysteriously unreliable.

## Example 5: Observing That the Application Is Fully Ready

There is a specific moment in Spring's startup when every bean has transitioned from initialization to the active phase, and the application as a whole is ready to serve traffic. Spring publishes an event at this moment called `ApplicationReadyEvent`, which you can listen for to run code that should only execute once everything is truly ready. This is a different thing from `@PostConstruct`, which only guarantees that the individual bean has initialized. `ApplicationReadyEvent` guarantees that the whole context is initialized, every bean is active, and the application is serving traffic.

```java
@Component
public class ReadinessAnnouncer {

    // This method is called by Spring once the entire application context
    // has fully initialized and every bean has transitioned to the active phase.
    // This is later than @PostConstruct, because @PostConstruct fires per-bean
    // during initialization, while this event fires once, after everything is done.
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // At this exact moment, every bean in the application is in the
        // active phase. You can safely reach into the context and use any
        // bean without worrying about whether it has finished initializing.
        System.out.println("Application is fully ready and serving traffic");

        // This is a good place to do work that depends on the entire system
        // being ready, such as announcing to a service discovery system that
        // this instance is available, warming up caches that involve multiple
        // beans, or running a self-test that touches several parts of the system.
    }
}
```

I want to draw a contrast that will help you understand the significance of this event. If you did the same work in a `@PostConstruct` method on some bean, you would be relying on the order in which Spring initializes beans to ensure that everything you touch is ready. Spring does guarantee that your bean's dependencies are active before your `@PostConstruct` runs, but it does not guarantee that every bean in the application is active. A bean that you do not directly depend on might not yet be initialized when your `@PostConstruct` runs. `ApplicationReadyEvent` gives you the stronger guarantee that the whole system is active, which is what you want for announcements, self-tests, and any other cross-cutting work that should happen exactly once after everything is ready.

There is also a more subtle benefit to using this event. It marks the transition into the long active phase in a way that is highly visible in logs and in application metrics. If you log "application ready" here, your log viewer can easily spot the transition from startup to active operation, and you can measure how long startup took with precision. This kind of observability is worth more than it seems, because it helps you notice when startup regressions creep in, which otherwise tend to go undiagnosed until they become painful.

## The Question of Scope and What Active Actually Means

There is a nuance about the active phase that is worth addressing explicitly, because it affects how you think about bean lifetime. Not every bean has the same definition of "active phase duration." Singleton beans, which are the default scope in Spring and the only scope we have been discussing so far, are active for the entire lifetime of the application context, from startup to shutdown. But Spring supports other scopes, and for those, the active phase is much shorter and has different characteristics.

A prototype-scoped bean, for example, has an active phase that begins when the bean is created and ends whenever the application stops using it. Spring creates a fresh instance every time the bean is requested, so each instance has its own active phase that is independent of other instances. A request-scoped bean, used in web applications, has an active phase that spans a single HTTP request. A session-scoped bean lives for the duration of a user's session. Each of these scopes has its own rhythm of initialization and destruction, and the active phase of each instance is bounded accordingly.

```java
@Service
@Scope("prototype")
public class PrototypeWorker {

    @PostConstruct
    public void init() {
        // This runs every time a new instance is created, which for a
        // prototype scope means every time someone asks for the bean.
        // Each instance has its own short active phase.
        System.out.println("PrototypeWorker created at " + Instant.now());
    }

    public void doWork() {
        // During this instance's active phase, it does work.
        // Once the caller stops holding a reference, the instance is eligible
        // for garbage collection, because Spring does not track prototype
        // beans after handing them out.
        System.out.println("Working...");
    }
}
```

Notice something surprising about prototype scope. Spring does not call `@PreDestroy` on prototype beans, because Spring stops tracking them after creation. The active phase of a prototype bean ends whenever the last reference to it goes out of scope, and cleanup becomes the caller's responsibility rather than Spring's. This is a significant departure from the singleton model we have been focused on, and it is worth knowing about because it changes how you think about resource management for non-singleton beans.

For our purposes, the important takeaway is that "fully initialized and serving requests" means different things for different scopes. For singletons, it means "from the end of startup until application shutdown," which is the long-lived active phase that dominates most discussions of the lifecycle. For shorter-lived scopes, the phrase still applies but compresses into much shorter windows. The lifecycle patterns we have been studying apply to all of them, but the relative importance of the active phase versus the init and destroy phases shifts based on how long the active phase actually lasts.

## A Final Reflection on the Significance of This Phase

Let me close by stepping back and naming what is really happening during the active phase, because the deeper meaning is something I want to stay with you long after you finish reading this. Every phase of the lifecycle we have studied so far has been about preparation. Construction prepared the bean's memory. Injection prepared its collaborators. Initialization prepared its internal state. Post-processing prepared its external behavior through proxies. All of this preparation exists for one reason, which is to make the active phase possible. The active phase is not a separate stage alongside the others; it is the reason the others exist at all.

This reframing is worth sitting with, because it changes how you think about the tradeoffs in your initialization code. If you spend five seconds during startup to cache a configuration value, you save a fractional second on every one of the millions of calls that will happen during the active phase. If you fail to validate a required field during initialization, you trade a loud startup failure for a quieter runtime failure that might not surface until hours into the active phase. Every design choice in the init phases is really a choice about what you want the active phase to look like, and understanding this lets you reason about initialization with clear priorities rather than vague habits.

The active phase is also where all the theoretical guarantees of the framework meet the messy reality of production. Concurrency is not a hypothetical concern during initialization, because Spring controls the threading there. Concurrency is very real during the active phase, where requests flood in from the network, scheduled tasks fire on their own threads, and the application's behavior depends on countless threads cooperating through shared state. The bugs that bring down production systems rarely happen during startup, because startup is brief and tightly choreographed. They happen during the active phase, where code runs for days and subtle bugs have time to accumulate their consequences. This is why thread safety, resource management, state consistency, and graceful degradation are not abstract topics but are the lived reality of keeping a Spring application healthy.

Here is the thought I most want you to carry forward. The lifecycle we have been studying together is interesting precisely because it frames the active phase. Every phase before it is scaffolding that holds up the active phase. Every phase after it, which we will explore next as we look at destruction, is cleanup that follows the active phase. The active phase is where your bean exists for a reason, where the abstract machinery of Spring becomes the concrete behavior of your application, and where the careful decisions you made in the earlier phases either pay off or come back to bite you. Understanding the active phase deeply, and especially understanding how the decisions made in other phases shape what the active phase can be, is ultimately what separates engineers who use Spring from engineers who truly understand it. You have been building that understanding across this entire conversation, and this phase is where all of it finally comes together.