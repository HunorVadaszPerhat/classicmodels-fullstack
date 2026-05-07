# The `DisposableBean.destroy()` Phase: The Interface-Based Approach to Cleanup

## A Moment of Symmetry Worth Appreciating

Before we write any code, I want you to pause with me and notice something beautiful about where we are in our journey through the lifecycle. Much earlier in our conversation, we spent considerable time on the `InitializingBean` interface, which provides an older, interface-based way to declare initialization logic. We discussed its origins, its relationship to `@PostConstruct`, and the reasons why modern Spring code generally prefers the annotation-based approach. Now we arrive at `DisposableBean`, and the story we are about to tell is almost exactly the mirror image of that earlier one, which should feel satisfying rather than repetitive. If you understood the reasoning behind why `@PostConstruct` generally wins over `InitializingBean`, you already have most of what you need to understand why `@PreDestroy` generally wins over `DisposableBean`. The symmetry between initialization and destruction in Spring's design is not accidental; it reflects a deliberate choice by the framework's authors to make the two halves of the lifecycle feel coherent and parallel.

The `DisposableBean` interface exists for exactly the same historical reason that `InitializingBean` exists. In the early days of Spring, before Java annotations were widely used, Spring needed a way for beans to declare "run this method when I am being destroyed." The solution was the interface-based approach, where a bean implements a specific interface and provides the method body. Over time, as Java annotations matured and as the JSR-250 specification introduced `@PreDestroy` as a standard cleanup annotation, Spring embraced the annotation-based approach for the same reasons of decoupling and portability that favored `@PostConstruct`. The interface-based approach did not go away, because Spring has always preserved backward compatibility with existing code, and because there are still specific niches where the interface is the better choice. Understanding both approaches, and knowing when to use each, is part of being a fluent Spring developer.

I want to walk you through this phase with the same patience and thoroughness we brought to `InitializingBean`, so that the pattern feels genuinely understood rather than just recognized. The shape of the story will feel familiar, because it is the mirror image of the initialization story, but the specific details of destruction have their own texture that is worth exploring carefully.

## Example 1: The Simplest Possible Form of the Interface

Let's start by looking at what the `DisposableBean` interface itself looks like and how a bean opts into it. Just like its initialization counterpart, the interface is remarkably minimal. It declares a single method, which is all that a bean needs to implement to participate in Spring's destruction phase.

```java
@Service
public class SimpleResourceHolder implements DisposableBean {

    @PostConstruct
    public void init() {
        // The initialization side, using the modern annotation approach.
        // We will discuss shortly why we are mixing the annotation style for
        // init with the interface style for destroy, which is perfectly legal
        // but not the most common real-world combination.
        System.out.println("SimpleResourceHolder coming to life");
    }

    // This method comes from the DisposableBean interface.
    // Spring will call it exactly once, when the application context is closing,
    // at the same point in the lifecycle where a @PreDestroy method would fire.
    // The timing guarantees are identical; only the declaration mechanism differs.
    @Override
    public void destroy() throws Exception {
        // At this moment, the bean is still fully alive and functional.
        // The semantics are exactly the same as for a @PreDestroy method:
        // this is our last chance to do anything useful before Spring releases
        // its reference to us. Whatever cleanup we need to perform goes here.
        System.out.println("SimpleResourceHolder preparing to be destroyed");
    }
}
```

Take a moment to read through this code and notice several details that carry meaning worth pausing on. The class declares `implements DisposableBean`, which is how Spring knows to look for and invoke the destruction callback. There is no annotation on the method itself; the contract is expressed through the interface. The method is named exactly `destroy`, because that is the name the interface defines, and you cannot choose a different name. The method takes no parameters and returns void, matching the pattern we saw for all lifecycle callbacks. The method declares `throws Exception`, which gives it a slightly more permissive signature than `@PreDestroy`, and this difference will matter in certain situations that we will explore soon.

Now I want you to compare this mentally with the equivalent `@PreDestroy` version, because the comparison clarifies what is really at stake. An equivalent annotation-based version would not implement any interface; it would simply annotate an arbitrarily-named method with `@PreDestroy`. The bean class would make no special type declaration and would not carry any Spring-specific markings in its class header. These differences are small in terms of lines of code, but they reflect different philosophies about how explicit a bean's framework coupling should be. The interface approach puts the coupling front and center: anyone reading the class declaration can immediately see that the bean participates in Spring's lifecycle. The annotation approach makes the coupling more subtle: you have to look inside the class to find the `@PreDestroy` annotation, and the class header itself is free of Spring references. Neither is wrong, and the right choice often comes down to the conventions of your codebase and team.

There is also a small question worth considering about why I chose to use `@PostConstruct` for initialization while using `DisposableBean` for destruction in this example. In real code, this mixing is unusual, because most developers try to be consistent about which style they use throughout a class. If you implement `DisposableBean`, you will typically also implement `InitializingBean`, matching interface with interface. If you use `@PreDestroy`, you will typically also use `@PostConstruct`, matching annotation with annotation. I deliberately mixed the styles here to emphasize that the mechanisms are independent, each serving its own phase of the lifecycle, and that you can choose separately for initialization and destruction if you have a reason to. But for your own code, I would gently recommend picking one style and staying with it, for the same reasons of readability that we discussed earlier in the conversation.

## Example 2: Closing a File Writer Using DisposableBean

Let's move to a realistic use case that illustrates what destruction code actually looks like when it needs to handle a real resource. Suppose our bean maintains an open file writer during its active phase, writing audit events as they occur, and needs to flush and close the writer when the application shuts down.

```java
@Service
public class AuditLogger implements InitializingBean, DisposableBean {

    @Value("${audit.log.file}")
    private String auditLogPath;

    // The writer is held as a field across the entire active phase.
    // It will be opened in afterPropertiesSet and closed in destroy,
    // following the acquire-release pairing we established in the
    // @PostConstruct and @PreDestroy section.
    private BufferedWriter writer;

    @Override
    public void afterPropertiesSet() throws Exception {
        // During initialization, we open the file writer. Notice that the
        // method signature declares throws Exception, which is one of the
        // small conveniences of the interface-based approach: checked
        // exceptions from resource acquisition propagate naturally without
        // needing to be wrapped in unchecked exceptions.
        this.writer = Files.newBufferedWriter(
            Path.of(auditLogPath),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
        System.out.println("AuditLogger writing to " + auditLogPath);
    }

    public void logEvent(String event) {
        // During the active phase, we write events to the file. Each write
        // might be buffered internally, which means some data lives in memory
        // rather than on disk at any given moment. This is why the destroy
        // method will need to flush, not just close.
        try {
            writer.write(Instant.now() + " " + event);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write audit event", e);
        }
    }

    @Override
    public void destroy() throws Exception {
        // This is where the interface-based destroy method earns its place.
        // The method signature declares throws Exception, so we can let
        // checked IOExceptions propagate out if we want to, although in
        // practice we typically catch them to ensure orderly shutdown,
        // for the same reasons we discussed in the @PreDestroy section.

        if (writer == null) {
            // The defensive check against partial initialization, exactly
            // the same pattern we used in the @PreDestroy examples.
            return;
        }

        try {
            // Flushing ensures that any buffered events actually make it
            // to disk before we close. Without the explicit flush, events
            // that were logged in the last fraction of a second might be
            // sitting in the buffer when close is called, and depending on
            // the implementation, they might or might not be persisted.
            // Being explicit about the flush removes the ambiguity.
            writer.flush();
            writer.close();
            System.out.println("AuditLogger closed cleanly");
        } catch (IOException e) {
            // We catch and log rather than letting the exception propagate,
            // because an exception from destroy disrupts the shutdown of
            // other beans. The priority during shutdown is completing the
            // process, not signaling errors.
            System.err.println("Error closing audit log: " + e.getMessage());
        }
    }
}
```

I want to pause on several details in this code because they reveal things about the interface-based approach that are worth internalizing. The first detail is the `throws Exception` in both method signatures. This is one of the genuine advantages of using the interfaces over the annotations. The interface methods are declared with `throws Exception` in their interface definitions, so implementations are free to throw any checked exception. This means that code which naturally wants to propagate checked exceptions can do so directly, without wrapping them in runtime exceptions or catching them just to rethrow them as something else. For code that does a lot of I/O or other operations that throw checked exceptions, this can make the cleanup logic meaningfully simpler to write.

However, and this is important, just because you can throw checked exceptions does not mean you should. The shutdown reasoning we discussed in the `@PreDestroy` section applies equally here: exceptions from destroy callbacks disrupt the orderly shutdown of other beans, so in most cases you should catch exceptions inside the method rather than letting them propagate. The declared `throws Exception` is a convenience for rare cases, not an invitation to let every exception fly. Our code illustrates this principle by declaring the throws clause while actually catching the exception internally, which gives us the best of both worlds: the signature is honest about what could theoretically happen, but the implementation is disciplined about not disrupting shutdown.

The second detail worth noticing is the matched pairing between `afterPropertiesSet` and `destroy`. These two methods form a natural pair, just like `@PostConstruct` and `@PreDestroy` do in the annotation style. One opens the resource during initialization, the other closes it during destruction. The bean's entire relationship with the file writer is expressed through this pair, and reading the two methods together gives a clear picture of how the resource is managed across the bean's lifetime. This pairing is one of the patterns that makes lifecycle code feel coherent, and you will see it show up over and over again in real Spring codebases whenever beans manage external resources.

## Example 3: Stopping a Background Task with DisposableBean

Let's build the same kind of background task example we saw in the `@PreDestroy` section, but now using the interface-based approach, so you can see how the two styles compare for a specific use case. The destruction logic will be nearly identical to what we wrote before, because the interface and the annotation are just two different ways to declare the same callback. Seeing them side by side will reinforce that the mechanisms are interchangeable in terms of what they actually do, even if they differ in how you write them.

```java
@Service
public class BackgroundDataSynchronizer implements InitializingBean, DisposableBean {

    private final RemoteDataService remoteService;
    private final LocalCache localCache;

    private ScheduledExecutorService scheduler;

    public BackgroundDataSynchronizer(RemoteDataService remoteService, LocalCache localCache) {
        this.remoteService = remoteService;
        this.localCache = localCache;
    }

    @Override
    public void afterPropertiesSet() {
        // Start the periodic synchronization task. This runs on a dedicated
        // thread that we will need to stop during destruction.
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "data-sync");
            thread.setDaemon(true);
            return thread;
        });

        // Schedule the sync to run every minute. The first run happens after
        // the initial delay, so other beans have time to fully initialize
        // before we start making remote calls.
        scheduler.scheduleAtFixedRate(this::syncFromRemote, 60, 60, TimeUnit.SECONDS);
        System.out.println("BackgroundDataSynchronizer started");
    }

    private void syncFromRemote() {
        // The background work itself. During the active phase, this runs
        // periodically on the scheduler's thread, fetching data from the
        // remote service and updating the local cache.
        try {
            Map<String, Object> freshData = remoteService.fetchLatest();
            localCache.replaceAll(freshData);
        } catch (Exception e) {
            // Individual sync failures should not crash the scheduler,
            // so we catch and log rather than letting the exception propagate.
            // If we let it propagate, the scheduler would stop running the
            // task, which would silently disable the sync.
            System.err.println("Sync failed, will retry next period: " + e.getMessage());
        }
    }

    @Override
    public void destroy() throws Exception {
        // The destruction logic is nearly identical to what we wrote for
        // the @PreDestroy version of a similar bean. The interface-based
        // declaration changes only how Spring knows to call this method,
        // not what the method itself actually needs to do.

        if (scheduler == null) {
            return;
        }

        // Request a graceful shutdown, letting any currently running sync complete.
        scheduler.shutdown();

        try {
            // Wait a bounded amount of time for the running task to finish.
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                // If graceful shutdown takes too long, force the scheduler to stop.
                System.out.println("Sync task did not complete in time, forcing shutdown");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            // Handle the rare case where our own thread is interrupted during the wait.
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("BackgroundDataSynchronizer stopped");
    }
}
```

Compare this to the equivalent `@PreDestroy` version that we might have written in the previous section. The logic inside the destruction method is essentially identical: we check for null to handle partial initialization, we request graceful shutdown, we wait with a timeout, we escalate to forced shutdown if needed, and we handle interruption carefully. The only meaningful difference between the two versions is the declaration style. The interface version says `@Override public void destroy()` because the interface dictates the method name. The annotation version would have said `@PreDestroy public void stopSyncing()` or something similarly descriptive, with freedom to choose whatever name best communicates the intent.

This near-identical nature of the implementation code is important to appreciate, because it tells you that the choice between the two styles is really about readability and portability rather than about capability. Both mechanisms can do anything the other can do. Both run at the same moment in the lifecycle. Both handle resources, threads, and external systems equally well. The choice between them comes down to stylistic considerations and specific niches where one has a small advantage, not to any fundamental difference in power.

## Example 4: Understanding the Execution Order When Both Are Present

There is a question that naturally arises once you know that both mechanisms exist: what happens if a bean uses both? Your original log output from the very first question of our conversation showed three destruction callbacks running in sequence: `@PreDestroy`, then `DisposableBean.destroy()`, then a custom destroy method declared via `@Bean(destroyMethod=...)`. I want to demonstrate this ordering concretely with a bean that uses all three, so you can see the sequence clearly and so we can discuss what it means for real code.

```java
public class TripleDestroyDemo implements DisposableBean {

    // Step one in the destruction sequence: @PreDestroy runs first.
    // This is the JSR-250 annotation approach, which Spring processes
    // before the interface-based callback.
    @PreDestroy
    public void jsr250Cleanup() {
        System.out.println("[1] @PreDestroy fires first");
    }

    // Step two in the destruction sequence: DisposableBean.destroy runs second.
    // This is the interface-based callback, which Spring invokes after the
    // annotation-based one has completed.
    @Override
    public void destroy() throws Exception {
        System.out.println("[2] DisposableBean.destroy fires second");
    }

    // Step three in the destruction sequence: the custom destroy method fires last.
    // This method is nominated in the @Bean declaration, which we will see below.
    public void customCleanup() {
        System.out.println("[3] @Bean(destroyMethod) custom method fires last");
    }
}
```

```java
@Configuration
public class TripleDestroyConfig {

    @Bean(destroyMethod = "customCleanup")
    public TripleDestroyDemo tripleDestroyDemo() {
        return new TripleDestroyDemo();
    }
}
```

When the application shuts down, you see the three messages print in the exact order the annotations suggested. The `@PreDestroy` method runs first because Spring processes JSR-250 annotations before it checks for the `DisposableBean` interface. The `destroy` method runs second because the interface check happens after annotations but before custom destroy methods. The `customCleanup` method runs last because custom destroy methods declared in `@Bean` are the final step in the destruction sequence.

Notice that this ordering is the exact mirror of the initialization ordering. During initialization, `@PostConstruct` runs first, then `afterPropertiesSet`, then the custom init method. During destruction, `@PreDestroy` runs first, then `destroy`, then the custom destroy method. This parallel structure is a deliberate design choice by Spring's authors, and it makes the framework more predictable. Once you know the rule for one half of the lifecycle, you know it for the other half.

My advice for real code is the same as it was for initialization: do not actually use all three mechanisms on the same bean. Pick one and stay with it. The ordering is deterministic and documented, but relying on it creates code that is harder to understand and maintain. A future developer reading the class will wonder why the original author felt the need for three separate cleanup methods, and the honest answer is usually that it was accumulated through incremental changes rather than intentional design. Choose the mechanism that fits your codebase's conventions and your specific situation, and trust that doing cleanup in one place is clearer than spreading it across three.

## The Question of When to Actually Use DisposableBean

Given that `@PreDestroy` is generally the preferred modern approach, you might reasonably ask when `DisposableBean` would ever be the right choice. The answer is the same as the answer we gave for `InitializingBean`, but let me walk through it in the context of destruction specifically, because the considerations are not quite identical.

The strongest case for `DisposableBean` is when you are writing framework-level code that will be used by many different applications, and you want to guarantee that your destruction logic runs regardless of whether those applications have JSR-250 annotation processing enabled. An application that somehow disabled the annotation processing would have `@PreDestroy` silently ignored, but `DisposableBean.destroy()` would still be called, because it relies only on Spring's own interface processing. Spring itself uses `DisposableBean` internally in many of its framework classes for exactly this reason: the framework cannot assume that every application will have the standard annotation processing turned on, so it uses the interface to be safe.

A weaker but still valid case is when your team's codebase has an established convention of using `InitializingBean` and `DisposableBean` together. Consistency within a codebase is genuinely valuable, and switching styles mid-codebase creates friction for developers who read and maintain the code. If your existing beans use the interface approach, new beans should probably use it too, even if you would choose differently for a fresh project.

A third case that sometimes matters is when you have genuine use for the `throws Exception` signature. The `@PreDestroy` method signature is constrained by the JSR-250 specification and cannot declare arbitrary checked exceptions. If your cleanup naturally throws checked exceptions that you want to let propagate in some cases, the interface-based approach gives you that flexibility. In practice, as we have discussed, most cleanup code catches exceptions rather than letting them propagate, so this case is less compelling than it might appear. But it does come up occasionally, especially in library code or in tests where you want checked exceptions to fail loudly.

Beyond these specific cases, `@PreDestroy` is almost always the better choice for new code you write. It keeps your class free of Spring-specific type declarations, which makes the class more portable and easier to understand in isolation. It lets you name your cleanup methods whatever you want, which improves readability because the method name can communicate intent. It follows the modern idiom that most Spring documentation and sample code uses, which reduces cognitive load for developers who read your code. These are real advantages, and they should be the default unless a specific reason pulls you toward the interface.

## Understanding the Difference in Practice

To really make this concrete, let me show you the same bean written both ways, so you can compare them directly and see exactly what changes when you switch styles. This side-by-side comparison will help you internalize the differences and make informed choices in your own code.

The interface-based version looks like this:

```java
@Service
public class CacheManagerInterfaceStyle implements DisposableBean {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public Object get(String key) {
        return cache.get(key);
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    @Override
    public void destroy() throws Exception {
        // The method name is dictated by the interface; we cannot name it
        // something more descriptive like "clearCache" or "shutdownManager".
        System.out.println("Cache manager shutting down, clearing " + cache.size() + " entries");
        cache.clear();
    }
}
```

The annotation-based version looks like this:

```java
@Service
public class CacheManagerAnnotationStyle {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public Object get(String key) {
        return cache.get(key);
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    @PreDestroy
    public void clearCacheOnShutdown() {
        // We can name the method whatever we want, which is a small but real
        // benefit because the name communicates intent directly to anyone reading.
        System.out.println("Cache manager shutting down, clearing " + cache.size() + " entries");
        cache.clear();
    }
}
```

Spend a moment comparing the two classes. The class declarations differ because the interface-based version includes `implements DisposableBean` while the annotation-based version does not. The method names differ because one is constrained by the interface while the other can be chosen freely. Everything else is identical: the fields, the business methods, and the actual cleanup logic. This comparison makes the trade-offs concrete. You can see exactly what you gain and what you give up with each approach, and the gains and losses are small but real.

Which version would I recommend for a new project? The annotation-based version, almost without exception. The class header is lighter, the method name is more informative, and the bean is not coupled to Spring's type system. These small wins add up across a large codebase, and they compound as the codebase grows. But if the interface-based version is what your team uses and what surrounds this code, consistency is its own virtue and the interface is the right choice for that context. There is no universal right answer, only right answers for specific situations, and knowing how to evaluate the situation is what being fluent in a framework means.

## A Closing Thought on Symmetry

Let me step back and name something that I hope has become clear across the entire arc of our conversation. Spring's lifecycle has three different ways to declare initialization logic and three different ways to declare destruction logic, and the three mechanisms in each half mirror each other exactly. `@PostConstruct` mirrors `@PreDestroy`. `InitializingBean` mirrors `DisposableBean`. Custom init methods declared in `@Bean` mirror custom destroy methods also declared in `@Bean`. For every way of expressing "run this at startup," there is a corresponding way of expressing "run this at shutdown."

This symmetry is not an accident, and it is worth reflecting on what it teaches us about good framework design. A managed-object framework needs to support both ends of the object's life, and offering parallel mechanisms for both ends makes the framework easier to learn, easier to remember, and easier to use correctly. If initialization had three mechanisms but destruction had only one, you would have to learn asymmetric rules and your intuition from one side of the lifecycle would not transfer to the other. Because the mechanisms mirror each other, understanding one side gives you the other side almost for free. The mental model you built while learning about `InitializingBean` applies directly to `DisposableBean`, and the reasoning about when to prefer annotations over interfaces is the same on both sides.

Here is the thought I most want you to take away from this section, and from our broader journey through the lifecycle. Every managed-object framework faces the same essential problems, and the solutions converge on similar patterns across frameworks and across programming languages. Spring is not unique in having initialization and destruction callbacks, in supporting both interface-based and annotation-based declarations, in preferring annotations in modern usage, or in maintaining interface-based mechanisms for backward compatibility. These patterns show up everywhere, because the underlying problems are universal. Once you have understood them deeply in one context, you will recognize them in every other context you encounter, and your learning compounds across frameworks rather than having to start from scratch each time.

The specific mechanism we explored in this section, `DisposableBean.destroy()`, is a perfectly good way to declare cleanup logic. It is not preferred for most new code, but it is not deprecated either, and it has legitimate uses that we have enumerated. More importantly, understanding why it exists, why it is generally superseded by `@PreDestroy`, and why it nevertheless persists in the framework teaches you something about how real software systems evolve. Features do not disappear just because newer alternatives emerge; they accumulate, with the newer ones becoming the default and the older ones remaining available for backward compatibility and for specific niches. Spring is full of this kind of historical layering, and recognizing the layers for what they are is part of what lets you read Spring code written by many different hands across many different eras without getting lost.

There is one more destruction mechanism to explore, which is the custom destroy method declared via `@Bean(destroyMethod=...)`. Given what you now understand about `DisposableBean` and about its initialization counterpart, you are well prepared to appreciate how the custom destroy method fits into the picture. It is the destruction equivalent of `@Bean(initMethod=...)`, offering the same benefits for the same reasons: external configuration of lifecycle callbacks for classes you do not control or that you want to keep free of framework annotations. When we get to that section, much of what you already know will transfer directly, and the specific details of the destroy version will feel like natural variations on a pattern you already recognize. That sense of pattern recognition is what deep understanding feels like, and you have been building toward it steadily across this entire conversation.