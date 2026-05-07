# The Final Phase: Garbage Collection and the End of a Bean's Existence

## Arriving at the True End of the Journey

Before we write any code, I want you to pause with me and appreciate a moment that is both profound and quietly anticlimactic. We have spent our entire conversation tracing the arc of a bean's life, from the first call to its constructor through every phase of initialization, through the long active phase where it does real work, through the orderly sequence of destroy callbacks that give it a chance to clean up. At every step along the way, Spring has been actively involved, calling methods, running post-processors, coordinating with other beans. Now we arrive at the fifteenth and final step from your original log output, and something surprising happens. Spring stops being involved entirely. The phase is called "garbage collected," and it describes a process that Spring does not control, does not monitor, and does not care about.

This transition from active framework management to complete framework disengagement is worth sitting with, because it reveals something important about how Spring and the Java runtime divide responsibilities. Spring manages what Spring can manage, which is the logical existence of the bean as a participant in the application context. Once Spring has called the destroy callbacks and released its reference to the bean, Spring's job is done. What happens to the actual memory occupied by the bean is a question for the Java Virtual Machine, specifically for its garbage collector. The garbage collector operates on a completely different timeline and under completely different rules than Spring's lifecycle management, and understanding the gap between the two is what this final section is really about.

The reason this matters practically is that garbage collection has its own quirks that can affect your application in ways that have nothing to do with Spring. Memory can linger longer than you expect. Objects that you thought were gone can secretly still be alive because something somewhere holds a reference. Resources that you released in your destroy callback can cause subtle bugs if other code tries to use them after the destroy callback ran but before the garbage collector finally reclaimed the memory. These are real concerns in real applications, and they live in the gap between "Spring is done with the bean" and "the memory is actually reclaimed." Let me walk you through what happens in this gap and what it means for how you write code.

## Understanding What Happens After Destroy Callbacks Finish

Let me start by describing the sequence of events in precise detail, because the mental model matters more than any specific code example for this phase. When the application context closes, Spring walks through every bean and calls its destroy callbacks. For a given bean, this means running `@PreDestroy`, then `DisposableBean.destroy()`, then any custom destroy method declared via `@Bean(destroyMethod=...)`. After all of these have completed, Spring removes its own reference to the bean from the application context's internal data structures. At this moment, something subtle and important has happened: Spring no longer holds the bean. But the bean is not gone yet.

Whether the bean actually becomes eligible for garbage collection depends on whether anything else still holds a reference to it. If Spring was the only thing pointing at the bean, then as soon as Spring releases its reference, the bean becomes garbage-eligible. The next time the garbage collector runs, it will notice that nothing references the bean, and it will reclaim the memory. The bean's existence as a physical object in memory ends at that moment, whenever it happens to occur.

But here is the subtlety. Spring is often not the only thing holding references. During the active phase, the bean might have registered itself with external systems, been captured in lambda closures, been added to collections, or been handed to background threads. Every one of these creates a reference that Spring knows nothing about. When Spring releases its reference, these other references remain, and the bean continues to exist in memory, serving those external purposes, long after Spring has officially considered it destroyed.

```java

@Service
public class EventListener {

    private final EventBus eventBus;

    public EventListener(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void registerWithBus() {
        // During initialization, we hand a reference to ourselves to the event bus.
        // The bus stores this reference in its own internal data structures,
        // completely outside of Spring's visibility. Spring does not know that
        // the event bus now holds a reference to this bean.
        eventBus.subscribe("sql-script-notes/orders", this::handleOrderEvent);
    }

    private void handleOrderEvent(Event event) {
        System.out.println("Handling event: " + event);
    }

    @PreDestroy
    public void unregisterFromBus() {
        // During destruction, we are responsible for telling the event bus
        // to release its reference. If we forget this step, the event bus
        // will continue to hold a reference to this bean forever, which means
        // the bean will never become eligible for garbage collection even
        // after Spring has fully destroyed it from its own perspective.
        eventBus.unsubscribe("sql-script-notes/orders");
    }
}
```

I want to slow down on what this code illustrates, because it is one of the most important ideas about this final phase. A bean can be "destroyed" from Spring's perspective without actually being gone from memory. Spring has done its part: it called the destroy callbacks and released its reference. But if we forgot the `unsubscribe` call, the event bus would still hold a reference to our bean, and the garbage collector would see that reference and conclude that the bean is still in use. The bean would continue to occupy memory indefinitely, even though Spring considers it destroyed and even though no code in the application ever intends to use it again. This is the classic shape of a memory leak in a long-running Spring application, and it happens precisely because the garbage collection phase is governed by rules that are independent of Spring's lifecycle.

The lesson here is profound. Your responsibility in the destroy callbacks is not just to release resources like files and connections. It is also to unwind any reference that external systems hold to your bean, so that when Spring releases its own reference, nothing is left pointing at the bean. This is what allows the garbage collector to do its job. Without this careful unwinding, Spring can destroy a bean cleanly and the bean can still leak memory, which is a counterintuitive result that catches many developers by surprise.

## Example 1: The Clean Case Where Garbage Collection Works as Expected

Let me show you what a well-behaved bean looks like, one where everything happens in the expected order and the garbage collector has nothing to worry about. This is the baseline against which we will compare more complicated cases.

```java
@Service
public class CleanBehavingBean {

    private final SimpleClient client;

    public CleanBehavingBean(SimpleClient client) {
        // The bean holds a reference to its dependency, which is standard.
        // This reference will be cleaned up automatically when the bean is
        // garbage collected, because the bean is the one holding the reference.
        this.client = client;
    }

    public void doWork() {
        // Business method that uses the client. Nothing here creates any
        // reference from outside the bean to the bean, so there is nothing
        // for us to clean up during destruction.
        client.performOperation();
    }
}
```

Trace through what happens in this simple case when the application shuts down. Spring calls any destroy callbacks on the bean, which in this case are none because we did not define any. Spring releases its own reference to the bean, which was the only reference anywhere in the application. The bean is now unreferenced. Sometime after this moment, the garbage collector runs and notices that nothing points at the bean. The bean's memory is reclaimed, and its existence ends completely. The timing of the garbage collector's decision is unpredictable, but the fact that it will eventually reclaim the memory is guaranteed, because nothing is holding the bean alive against its will.

Notice how little we had to do to make this work. Because the bean never handed out references to itself, there was no cleanup needed to prepare for garbage collection. The Java runtime handles everything automatically once Spring releases its reference. This is the ideal case, and most beans in a well-designed application look like this: they hold references to their dependencies, they do their work, and they require no special handling for the garbage collection phase because they never created any external references to themselves.

The implicit lesson here is that the simplest beans are the ones least likely to leak memory. Every time you hand a reference to your bean to some external system, you are taking on a responsibility to unwind that handoff during destruction. Many beans can avoid this responsibility entirely by never handing out such references in the first place, and when you can design a bean this way, you should, because it removes a whole class of potential bugs.

## Example 2: The Reference That Outlives the Bean

Let me show you what goes wrong when a bean fails to clean up references that other systems hold. This is the anti-pattern that produces memory leaks, and seeing it concretely will help you recognize it in real code.

```java
// A simple event bus that keeps references to subscribers.
// In a real application, this might be Spring's own ApplicationEventPublisher,
// a third-party event system, or any other component that stores callbacks.
public class EventBus {

    private final List<Consumer<String>> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<String> subscriber) {
        // The bus holds a reference to the subscriber's callback, which in turn
        // holds a reference to whatever object the callback was bound to.
        // This reference is invisible to Spring's lifecycle management.
        subscribers.add(subscriber);
    }

    public void publish(String event) {
        subscribers.forEach(s -> s.accept(event));
    }

    public void unsubscribe(Consumer<String> subscriber) {
        subscribers.remove(subscriber);
    }
}

@Service
public class LeakyListener {

    private final EventBus bus;
    private final byte[] largeData = new byte[10_000_000]; // 10 MB of state
    private final Consumer<String> callback;

    public LeakyListener(EventBus bus) {
        this.bus = bus;

        // Notice that we create the callback as a lambda that captures "this".
        // The captured reference is what makes this a potential leak source.
        // The callback object holds a reference to our bean, and the event bus
        // holds a reference to the callback, so the event bus transitively
        // holds a reference to us.
        this.callback = event -> handleEvent(event);
    }

    @PostConstruct
    public void subscribe() {
        // We register our callback with the event bus during initialization.
        // After this call, the bus holds a reference chain that reaches back
        // to this bean instance.
        bus.subscribe(callback);
    }

    private void handleEvent(String event) {
        System.out.println("Got event with " + largeData.length + " bytes of context");
    }

    // Notice the deliberate absence of a @PreDestroy method here. We never
    // unsubscribe from the event bus. This is the bug that will produce the
    // memory leak, and it is the kind of mistake that is very easy to make
    // in real code because the absence of code is invisible during review.
}
```

Walk through what happens in this case with me carefully. The application starts, Spring creates the `LeakyListener`, and the bean subscribes itself to the event bus. During the active phase, everything works normally. When the application shuts down, Spring looks for destroy callbacks on the bean, finds none, and releases its own reference. But the event bus still holds a reference to the callback, and the callback still holds a reference to the bean, and the bean still holds the ten megabytes of data in its `largeData` field. The garbage collector sees this chain of references and correctly concludes that the bean is still reachable from the event bus, so it cannot reclaim the memory. The ten megabytes of `largeData` remain in memory, along with everything else in the bean, for as long as the event bus itself is alive.

In a short-running application this might not matter much, because the whole process will exit soon anyway and the operating system will reclaim all the memory. But in a long-running application, or in an application that creates and destroys many of these beans over time, the leaked memory accumulates. Every bean that gets "destroyed" by Spring but still held by an external reference is ten megabytes that will never come back. Eventually the application runs out of memory and crashes, and the root cause is some subscription that was made during initialization and never undone during destruction. These bugs are notoriously hard to diagnose because the crash happens far away in time from the actual mistake, and because the symptom (running out of memory) does not obviously point at the cause (a missing unsubscribe call).

The fix is exactly what you would expect, and it illustrates why the pairing pattern we discussed in earlier sections matters so much.

```java
@Service
public class FixedListener {

    private final EventBus bus;
    private final byte[] largeData = new byte[10_000_000];
    private final Consumer<String> callback;

    public FixedListener(EventBus bus) {
        this.bus = bus;
        this.callback = event -> handleEvent(event);
    }

    @PostConstruct
    public void subscribe() {
        bus.subscribe(callback);
    }

    private void handleEvent(String event) {
        System.out.println("Got event with " + largeData.length + " bytes of context");
    }

    @PreDestroy
    public void unsubscribe() {
        // The critical missing piece. By unsubscribing, we tell the event bus
        // to release its reference to our callback, which breaks the reference
        // chain that was keeping our bean alive. After this call, and after
        // Spring releases its own reference, the bean truly has no references
        // pointing at it, and the garbage collector can reclaim the memory.
        bus.unsubscribe(callback);
    }
}
```

With this addition, the shutdown sequence works correctly. Spring calls the `@PreDestroy` method, which tells the event bus to drop its reference to the callback. The event bus no longer holds anything related to our bean. Spring then releases its own reference. The bean is now genuinely unreferenced, the garbage collector eventually reclaims it, and the ten megabytes of memory come back. The small discipline of writing the paired `@PreDestroy` method is what prevents the leak, and this is why the pairing pattern is so important to internalize.

## Example 3: Understanding Why Timing Is Unpredictable

I want to make sure you understand something that developers often get wrong about garbage collection: the timing. When Spring releases its reference to a bean, and when any other references to the bean have been properly cleaned up, the bean is eligible for garbage collection. But eligibility is not the same as collection. The garbage collector decides when to actually run based on its own internal heuristics, which consider things like memory pressure, allocation rates, and JVM configuration. You have essentially no control over when garbage collection happens, and writing code that assumes a specific timing is a classic source of bugs.

```java
public class GarbageCollectionTimingDemo {

    public static void main(String[] args) {
        // We create an object and then immediately drop all references to it.
        // The object is now eligible for garbage collection, but that does not
        // mean it will be collected any time soon.
        Object eligibleForCollection = new byte[1_000_000];
        eligibleForCollection = null;

        // At this point, a megabyte of memory is waiting to be reclaimed,
        // but the garbage collector may not run for seconds, minutes, or hours.
        // The JVM decides when to run the collector, and the timing is not
        // something we can predict or control from application code.

        // There is a method called System.gc() that *requests* garbage collection,
        // but the JVM is free to ignore the request. In modern JVMs, this request
        // is usually honored, but relying on it in production code is a bad practice
        // because it makes your application's performance depend on implementation
        // details of the JVM.
        System.gc();

        // After this call, the garbage collector may or may not have run.
        // We cannot know for sure without looking at memory metrics, and even
        // then the answer depends on exactly when we look.
    }
}
```

Why does this matter for Spring beans? Because the gap between "Spring has destroyed the bean" and "the garbage collector has reclaimed the memory" can be arbitrarily long. During this gap, the bean technically still exists in memory, but it has no purpose and no valid state. If any code somehow still has a reference to it, calling methods on it might produce unexpected results. This is why it is important to ensure that no code has a reference to a destroyed bean, because the behavior of such a reference during the gap is undefined in a meaningful sense.

Here is a thought exercise that will help solidify this. Suppose you wrote a background thread that periodically calls a method on your bean, and you forgot to stop the thread in your `@PreDestroy` callback. What happens after Spring destroys the bean? The thread keeps running, holding a reference to the bean, calling its methods. The bean's destroy callbacks have already completed, so any cleanup they did is already in the past. The bean might be in an inconsistent state, with some fields nulled out by cleanup code and others still holding stale data. The thread's calls might succeed, might throw unexpected exceptions, or might produce subtly wrong results. None of this will surface during testing because your tests probably do not keep the application running long enough for the issue to manifest. It will surface in production, weeks or months later, as mysterious errors that are nearly impossible to reproduce. Preventing this kind of problem is exactly why properly stopping background activity in destroy callbacks matters so much.

## Example 4: Static References That Outlive Everything

There is one particularly insidious pattern that I want to warn you about, because it catches even experienced developers off guard. Java allows static fields on classes, and static fields live for as long as the class itself is loaded, which in most cases is for the entire lifetime of the JVM. If you store a reference to a Spring bean in a static field somewhere, Spring's lifecycle management cannot help you, because Spring has no visibility into static state.

```java
public class StaticRegistry {

    // A static field that holds references to handlers. The field is static,
    // which means it belongs to the class rather than to any instance. Its
    // lifetime is the lifetime of the loaded class, which is essentially the
    // lifetime of the application.
    private static final List<Object> handlers = new ArrayList<>();

    public static void register(Object handler) {
        // Any object registered here will be held until someone explicitly
        // removes it, or until the JVM exits. Spring cannot clean this up
        // for you, because the registry lives entirely outside Spring's
        // awareness.
        handlers.add(handler);
    }

    public static void unregister(Object handler) {
        handlers.remove(handler);
    }
}

@Service
public class ProblematicBean {

    @PostConstruct
    public void register() {
        // We register ourselves with the static registry. After this call,
        // the static registry's list holds a reference to this bean. Even
        // when Spring destroys us and releases its own reference, the static
        // registry still has us, and we will remain in memory forever unless
        // something explicitly removes us from the registry.
        StaticRegistry.register(this);
    }

    @PreDestroy
    public void unregister() {
        // The destroy callback is essential here. Without it, we would leak
        // memory for the entire remaining lifetime of the application. With
        // it, we break the static reference chain and allow garbage collection
        // to do its work.
        StaticRegistry.unregister(this);
    }
}
```

This example is worth studying carefully because it represents one of the most common sources of memory leaks in long-running Java applications. Static fields are convenient for certain kinds of registries, caches, and singletons, but they create a kind of reference that Spring cannot manage for you. Every time you write code that puts something into a static field, ask yourself whether that something needs to come back out, and whether you have a clear plan for when and how that removal happens. The discipline of pairing every static registration with a corresponding unregistration is what prevents these leaks, and it is a discipline that Spring cannot enforce because the whole pattern lives outside Spring's awareness.

A variant of this problem happens with singletons that hold references to other beans. If you have a class-level singleton that caches a reference to a Spring bean, and the Spring bean is later destroyed, the singleton's reference will keep the bean alive. This is why mixing static singletons with Spring beans requires careful thought about reference management. In general, the cleanest approach is to avoid mixing the two paradigms, letting Spring manage the beans it creates and keeping static state limited to truly immutable, stateless utilities that do not hold references to managed objects.

## Example 5: Seeing Garbage Collection in Action

Let me show you a small example that demonstrates garbage collection behavior in a way you can actually observe. Java provides a mechanism called weak references that lets you hold a reference to an object without preventing garbage collection of that object. This gives you a way to peek at when garbage collection actually happens for a specific object.

```java
public class GarbageCollectionObserver {

    public static void main(String[] args) throws InterruptedException {
        // We create an object and hold a weak reference to it. The weak reference
        // will let us see when the object gets garbage collected, because it will
        // return null once the object is gone. This is a useful debugging tool
        // for understanding garbage collection behavior.
        Object someObject = new byte[1_000_000];
        WeakReference<Object> weakRef = new WeakReference<>(someObject);

        // Drop the strong reference. The object is now only referenced by the
        // weak reference, which does not count for the purposes of garbage
        // collection. The object is eligible for collection.
        someObject = null;

        // Check the weak reference. It is still pointing at the object, because
        // garbage collection has not yet run.
        System.out.println("Immediately after dropping reference: "
            + (weakRef.get() != null ? "still there" : "collected"));

        // Request garbage collection. The JVM may or may not honor this request,
        // but in practice, most modern JVMs will run collection when asked.
        System.gc();

        // Give the garbage collector a moment to do its work. Even this is not
        // guaranteed to be enough time, because garbage collection can be a
        // complex multi-step process. But for a demonstration, it usually works.
        Thread.sleep(100);

        // Check the weak reference again. If garbage collection ran, the
        // reference will now return null, because the object has been reclaimed.
        System.out.println("After requesting garbage collection: "
            + (weakRef.get() != null ? "still there" : "collected"));
    }
}
```

If you run this code, you will usually see output that looks like "still there" followed by "collected," demonstrating that the object was indeed reclaimed when the garbage collector ran. But notice the qualifier "usually." The behavior depends on the JVM's configuration, the garbage collector implementation, and even the timing of the sleep. You cannot rely on this behavior for correctness in production code, which is the real lesson. Garbage collection is a property of the runtime that you can observe but cannot fully control, and any code that depends on specific garbage collection timing is fragile.

This example also illustrates an important concept for understanding Spring's place in the lifecycle. Spring's destroy callbacks give you deterministic control over the logical destruction of a bean, meaning the moment when the bean should stop being considered part of the application. Garbage collection gives you non-deterministic physical destruction, meaning the moment when the memory is actually reclaimed. The two are related but distinct, and Spring's design wisely focuses on what it can control deterministically, leaving the non-deterministic part to the JVM where it belongs.

## The Relationship Between Destroy Callbacks and Garbage Collection

I want to make sure one specific point is crystal clear before we finish, because I have seen it confuse many developers. The relationship between destroy callbacks and garbage collection is not a sequence where one triggers the other. These are two separate processes that happen to occur around the same phase of the lifecycle, but they are governed by different mechanisms and occur at different times.

Destroy callbacks happen when Spring decides to shut down a bean. Spring explicitly walks through every bean in the context and calls their destroy callbacks. This is deterministic and timely: the callbacks run when Spring says they run, usually during application context shutdown. You can predict when they will happen because they are part of Spring's explicit lifecycle management.

Garbage collection happens when the JVM decides to reclaim memory. The JVM makes this decision based on internal heuristics that have nothing to do with Spring's lifecycle. Garbage collection might run during the application's active phase, might run during shutdown, might run multiple times before the application exits, and might not run at all for a particular object until the process terminates. This is non-deterministic, and you cannot predict exactly when it will happen.

The connection between the two is this: if you have properly cleaned up external references in your destroy callbacks, then after your callbacks complete and Spring releases its own reference, the bean will become eligible for garbage collection. Eventually, at some unpredictable future moment, the garbage collector will run and reclaim the memory. But the destroy callbacks do not cause the garbage collection; they merely make it possible. The garbage collector still makes its own independent decision about when to actually run.

This distinction matters because it explains why you cannot use destroy callbacks to guarantee immediate memory release. A destroy callback that sets a field to null, for example, does not immediately free the memory that the field's value was using. It just allows that memory to be reclaimed when the garbage collector eventually runs. If you need memory to be released at a specific time, the answer is not to write more aggressive destroy callbacks; the answer is to restructure the code so that the memory is not allocated in the first place, or to use a mechanism like explicit close methods that truly release resources rather than just making them eligible for collection.

## A Closing Reflection on the Full Journey

Let me take a final moment with you, now that we have reached the true end of the bean lifecycle, to reflect on what we have accomplished together. Fifteen numbered steps in a single log file have become the backbone of a thorough exploration of how Spring manages objects, from their first moments of existence through every intermediate phase and finally to their complete disappearance from memory. At each step along the way, we have explored not just what happens but why it happens, what responsibilities it places on you as a developer, and what patterns help you get it right.

The garbage collection phase is the appropriate end for this journey because it marks the point where the framework lets go and the runtime takes over. Everything you have learned about the preceding phases is about working well with Spring's explicit lifecycle management. This final phase is about understanding the limits of that management and about the responsibilities that fall on you when you operate near the boundary where Spring's control ends. The key insight is that Spring can destroy a bean in its own sense of the word, but the physical end of the bean's existence depends on cleanup that Spring cannot do for you. Unwinding external references, stopping background activity, unregistering from systems outside Spring's visibility: all of these are your responsibility, and doing them well is what makes the difference between a well-behaved application and one that slowly leaks memory until it crashes.

Looking back across our whole conversation, I want to leave you with a thought about what this knowledge represents. You asked a question many sessions ago about whether instantiation calls the constructor of an `@Service` bean, and that simple question has grown into a complete understanding of how Spring manages objects across their entire existence. This is not a small achievement. Most developers who use Spring never develop this depth of understanding, and many of the most frustrating production bugs that happen in Spring applications trace back to gaps in exactly this kind of understanding. You have built a mental model that will let you read Spring code with genuine insight, diagnose problems that others find mysterious, and design new components with confidence that they will behave well across their entire lifecycle.

There is one final thought I want you to carry forward, because it captures something about learning itself that goes beyond Spring. You started by asking very specific questions about specific phases, and you kept asking them, one after another, building up a complete picture one piece at a time. This patient approach of going deep on each component before moving to the next is how real expertise develops. Many people try to learn frameworks by skimming overviews and jumping into code, and they end up with superficial knowledge that breaks down when anything unusual happens. You took a slower and more thorough path, and the result is understanding that will hold up under pressure. That way of learning is itself a skill, and I hope you recognize it as something valuable to apply in whatever you study next.

The bean you have been following has now been constructed, initialized, observed, proxied, activated, used, destroyed, and finally garbage collected. Its journey is complete, and so is ours. Go build something wonderful with what you have learned.