# Setting Up Logging and Tracing Context in `postProcessBeforeInitialization()`

## Why Logging and Tracing Need a Dedicated Setup Moment

Before we touch any code, I want to build up the problem we are solving so that the solution feels natural rather than arbitrary. Picture a running application in production. A user clicks a button in their browser, which triggers an HTTP request that arrives at your server. The request flows through a controller, which calls a service, which calls another service, which calls a repository, which talks to the database. Along the way, each component writes log messages. A single request might produce twenty log lines scattered across six different classes, and your production system might be handling thousands of requests concurrently, each producing their own twenty log lines interleaved with everyone else's. If you look at the raw log file, you see a waterfall of messages from different requests all mixed together, with no easy way to reconstruct the story of any single request.

This is the problem that logging and tracing context solves. The idea is to attach a small piece of metadata to every log message produced during the handling of a single request, so that later you can filter the logs by that metadata and see only the messages belonging to one specific request. The most common piece of metadata is a "request ID" or "trace ID," a unique string generated when the request arrives and propagated through every component that participates in handling it. When you look at the logs afterward, you can search for that ID and see a clean chronological story of what happened during that one request.

The mechanism for carrying this metadata in Java is called the Mapped Diagnostic Context, usually abbreviated as MDC. It is essentially a thread-local map of key-value pairs that your logging framework automatically includes in every log message. Most modern logging libraries, including Logback and Log4j, support MDC out of the box. Distributed tracing systems like OpenTelemetry and Zipkin use the same idea but extend it across service boundaries by propagating the trace ID through HTTP headers or message metadata.

So where does `postProcessBeforeInitialization()` fit into all of this? The answer is that many components of a well-instrumented application need to know their own identity for logging purposes, or need to have a logger configured with contextual information before they do any work. If every bean had to set this up manually in its `@PostConstruct` method, you would have the same tedious boilerplate repeated across dozens of classes. A `BeanPostProcessor` lets you handle the setup once, centrally, for every bean that needs it. The "before init" phase is the right moment because we want the logging machinery to be ready before the bean itself starts producing log messages, which can happen as early as inside `@PostConstruct`.

Let's see this abstract idea turn into concrete, practical code.

## Example 1: Automatically Injecting a Named Logger

The most basic logging-related setup task is to give every bean a logger that knows the name of the class it serves. Developers typically write this by hand at the top of every class, creating a line like `private static final Logger log = LoggerFactory.getLogger(MyClass.class);` in dozens of places. It is not hard to write, but it is easy to get wrong, especially when someone copies a class and forgets to update the class reference inside the getLogger call. We can automate this with a processor and a small annotation.

We start with the annotation that marks a field as wanting a logger injected.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectLogger {
}
```

A bean uses it by simply declaring a logger field and marking it with the annotation. Notice that the developer does not need to specify the class name anywhere, because our processor will figure that out automatically from the field's containing class.

```java
@Service
public class OrderService {

    @InjectLogger
    private Logger log;

    @PostConstruct
    public void init() {
        // The logger is ready by the time we get here,
        // and it already knows that this class is OrderService
        log.info("OrderService initialized");
    }
}
```

The processor populates the field by creating a logger named after the bean's class. The key detail to appreciate here is that when we create the logger, we pass the bean's actual class, which means the logger's name matches what a handwritten logger would have been. This preserves the behavior people expect from their logging configuration, where log levels are often set per-package or per-class.

```java
@Component
public class LoggerInjector implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Walk every field on the bean's class, including inherited ones
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            // Only populate fields that explicitly asked for a logger
            if (!field.isAnnotationPresent(InjectLogger.class)) return;

            // Create a logger named after the bean's own class, matching
            // the name a manually declared logger would have had
            Logger logger = LoggerFactory.getLogger(bean.getClass());

            // Reflection requires us to bypass access for private fields
            field.setAccessible(true);
            field.set(bean, logger);
        });
        return bean;
    }
}
```

Take a moment to appreciate what this small piece of code just accomplished. In a large application with hundreds of classes, the cumulative savings in boilerplate are significant, but the more subtle benefit is consistency. Every logger in the system now has the correct name by construction, because the processor derives the name from the actual class rather than relying on a human to type it correctly. A whole category of silly bugs, where a logger declared in one class accidentally uses another class's name due to a copy-paste mistake, simply cannot happen in a codebase that uses this pattern.

There is a small exercise worth doing in your head to test your understanding. If you applied this processor and then looked at the logger's reported class name in the log output, whose name would you see: the logger would carry the name of the bean's concrete class, such as `OrderService`, and not the processor's class. This is because we passed `bean.getClass()` to the logger factory, and `getClass()` returns the runtime class of the object we were handed. This is exactly what we want, and it illustrates a nice property of doing the work at runtime instead of at compile time, where we could never have access to the concrete class so readily.

## Example 2: Tagging Beans with Logging Context via MDC

The second example moves us closer to the real purpose of logging context. Suppose we want certain beans to automatically tag every log message they produce with the name of the bean and a unique identifier, so that log messages from different instances of the same class can be distinguished. This is especially useful for multi-tenant systems where one bean per tenant might be running and you want to see which tenant produced each log line.

We define an annotation that marks a bean as wanting to have its methods wrapped with MDC context.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WithLoggingContext {
    String key() default "beanName";
}
```

A bean opts in by adding the annotation. It does not need to know anything about MDC or how the context is propagated; it only needs to declare its intent.

```java
@Service
@WithLoggingContext(key = "service")
public class PaymentService {

    @InjectLogger
    private Logger log;

    public void charge(String customer, int amount) {
        // When this method runs, MDC will already contain "service=PaymentService",
        // so any logger configuration that includes %X{service} will print it automatically
        log.info("Charging customer {} for {} cents", customer, amount);
    }
}
```

The processor replaces the bean with a proxy that establishes the MDC context before each method call and clears it afterward. This is where we reuse the dynamic-proxy technique from the previous section, but applied to a different purpose. The pattern of wrapping a bean in a proxy to add cross-cutting behavior is showing up for the second time now, which should reinforce why that technique is so central to Spring's design.

```java
@Component
public class LoggingContextInstaller implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();
        WithLoggingContext annotation = beanClass.getAnnotation(WithLoggingContext.class);

        // Only wrap beans that opted in via the annotation
        if (annotation == null) return bean;

        // The MDC key under which we'll store the context value
        String contextKey = annotation.key();
        // The value we'll put into MDC, derived from the bean's simple class name
        String contextValue = beanClass.getSimpleName();

        // Build a proxy that adds MDC context around every method call.
        // Proxies require interfaces, which matches our earlier discussion.
        return Proxy.newProxyInstance(
            beanClass.getClassLoader(),
            beanClass.getInterfaces(),
            (proxyInstance, method, args) -> {
                // Set the context before the method runs, so any log calls
                // made during the method body include the context automatically
                MDC.put(contextKey, contextValue);
                try {
                    return method.invoke(bean, args);
                } finally {
                    // Always remove the key afterward, so the context does not
                    // leak into unrelated work on the same thread
                    MDC.remove(contextKey);
                }
            }
        );
    }
}
```

There is a detail in this code that I want to spend a moment on because it illustrates something important about thread-local state in general, and MDC in particular. The `try/finally` block is not decorative. If we did not clear the MDC key after the method returned, the key would remain in the thread's MDC map, and when that same thread was later reused to handle a different request or a different bean's work, the leftover key would silently contaminate those unrelated log messages. This is the kind of subtle bug that is nearly impossible to debug by reading logs, because the logs themselves are the source of the misleading information. Any code that modifies thread-local state must take responsibility for cleaning it up, and `try/finally` is the only reliable way to guarantee cleanup even when the underlying method throws an exception. Keeping this discipline is one of the quiet arts of writing correct logging infrastructure.

## Example 3: Assigning a Per-Bean Unique Identifier for Debugging

The third example shows a pattern that is especially valuable in systems with many instances of similar beans, such as worker pools or prototype-scoped components. We want each bean to carry a short, stable identifier that appears in its log messages, so that when we are reading the logs we can tell at a glance whether two messages came from the same instance or from two different ones.

We start with an annotation that marks a bean as wanting this treatment, and a field where the identifier will be injected so the bean itself can use it if needed.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InstanceId {
}
```

A bean uses it by declaring a field that will hold the generated identifier.

```java
@Service
public class BackgroundWorker {

    @InjectLogger
    private Logger log;

    @InstanceId
    private String id;

    @PostConstruct
    public void start() {
        // By the time this runs, the processor has generated a unique id
        // and placed it in the field, so we can include it in logs
        log.info("Worker {} starting up", id);
    }

    public void doWork() {
        log.info("Worker {} processing a task", id);
    }
}
```

The processor generates a short identifier and populates the field. We use a simple counter combined with the class name, which is enough to distinguish instances within a single run of the application. For stronger identifiers, you could use `UUID.randomUUID()` at the cost of slightly longer log lines.

```java
@Component
public class InstanceIdAssigner implements BeanPostProcessor {

    // Atomic counter shared across all beans this processor handles.
    // AtomicInteger gives us safe increments even if beans are created in parallel.
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            if (!field.isAnnotationPresent(InstanceId.class)) return;

            // Build a short, human-readable identifier like "BackgroundWorker#3"
            String id = bean.getClass().getSimpleName() + "#" + counter.incrementAndGet();

            field.setAccessible(true);
            field.set(bean, id);
        });
        return bean;
    }
}
```

Notice something worth appreciating about this design. The identifier generation is centralized inside the processor, which means every bean in the system that uses the `@InstanceId` annotation receives an identifier from a single counter. This gives you a total ordering of bean creation across the entire application, which can itself be useful information when debugging startup problems. If instead each bean generated its own identifier, you would lose that coordination. This is a subtle but recurring theme across all our examples: by centralizing cross-cutting logic in a processor, you gain opportunities for coordination that would be awkward or impossible if each bean handled the logic independently.

## Example 4: Combining Logger Injection, MDC Context, and Instance Identifiers

The final example in this section pulls together the ideas we have developed and shows how they naturally combine into a richer setup. In a mature logging infrastructure, you would typically want all three capabilities working together: a logger named after the class, an MDC context that identifies the bean's role, and an instance identifier that distinguishes individual instances. A single processor can provide all three, which is what you would likely build in a real project to keep startup efficient.

The bean opts in by using the relevant annotations, and the processor handles all three concerns in a single pass.

```java
@Service
@WithLoggingContext(key = "service")
public class EmailDispatcher {

    @InjectLogger
    private Logger log;

    @InstanceId
    private String id;

    public void dispatch(String recipient, String subject) {
        // This single log line benefits from all three pieces of setup:
        // the logger is named after EmailDispatcher, the MDC has service=EmailDispatcher
        // thanks to the proxy, and the id field tells us which instance did the work
        log.info("Dispatcher {} sending email to {} with subject '{}'", id, recipient, subject);
    }
}
```

The combined processor runs all three transformations in the correct order. The logger injection happens first because the other transformations do not depend on it but the bean's own init logic might. The instance identifier is assigned next because it is a simple field injection. The proxy wrapping happens last because it needs to return the proxy in place of the original bean, and we want all the other setup to have completed on the original before we wrap it.

```java
@Component
public class LoggingInfrastructureProcessor implements BeanPostProcessor {

    private final AtomicInteger instanceCounter = new AtomicInteger(0);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Step one: inject the logger field if requested
        injectLogger(bean);

        // Step two: assign an instance identifier if requested
        assignInstanceId(bean);

        // Step three: wrap in a proxy if the bean requested MDC context.
        // This step returns a new object, so it must come last; any earlier
        // transformations apply to the original bean, which becomes the
        // proxy's delegate target.
        return wrapWithMdcContext(bean);
    }

    private void injectLogger(Object bean) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            if (!field.isAnnotationPresent(InjectLogger.class)) return;
            Logger logger = LoggerFactory.getLogger(bean.getClass());
            field.setAccessible(true);
            field.set(bean, logger);
        });
    }

    private void assignInstanceId(Object bean) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            if (!field.isAnnotationPresent(InstanceId.class)) return;
            String id = bean.getClass().getSimpleName() + "#" + instanceCounter.incrementAndGet();
            field.setAccessible(true);
            field.set(bean, id);
        });
    }

    private Object wrapWithMdcContext(Object bean) {
        Class<?> beanClass = bean.getClass();
        WithLoggingContext annotation = beanClass.getAnnotation(WithLoggingContext.class);
        if (annotation == null) return bean;

        String contextKey = annotation.key();
        String contextValue = beanClass.getSimpleName();

        return Proxy.newProxyInstance(
            beanClass.getClassLoader(),
            beanClass.getInterfaces(),
            (proxyInstance, method, args) -> {
                MDC.put(contextKey, contextValue);
                try {
                    return method.invoke(bean, args);
                } finally {
                    MDC.remove(contextKey);
                }
            }
        );
    }
}
```

The ordering of the three steps is worth lingering on, because it embodies a principle that applies to many combined transformations. Transformations that modify the bean in place, such as setting fields via reflection, should happen before transformations that substitute the bean with a different object. If we had wrapped the bean in a proxy first and then tried to set a field on the proxy, the reflection call would operate on the proxy's synthetic class and the field would not exist there. By doing all field-level work first and substitution last, we keep each step operating on the right target. This kind of ordering concern comes up repeatedly when you stack multiple processors or combine multiple transformations within one, and recognizing it early will save you from frustrating debugging sessions later.

## A Thought on What We Are Really Building

Let me close this section by stepping back and naming what the examples in this section really represent. We are building a layer of infrastructure that sits between the application's business logic and the operational concerns of running software in production. Logging, tracing, instance identification, and MDC context are all concerns that the business logic does not want to know about but that the operators of the system desperately need. The processors we have written carry that burden on the application's behalf, letting each bean focus on what it does while the infrastructure takes care of making it observable.

This separation of concerns is more than a stylistic preference. In production systems, the quality of your observability infrastructure often determines whether an incident takes five minutes to diagnose or five hours, and whether you catch a subtle bug during normal operation or only after a customer complains. A well-instrumented application is not magically well-instrumented; someone had to build the instrumentation, and the patterns we have been practicing are exactly the patterns that let you build it well. The fact that you can do all of this without modifying the business logic of your beans is what makes the approach sustainable as the system grows, because new beans automatically inherit the full observability package just by following the conventions.

Here is a thought exercise to carry with you after this section. Suppose you were asked to add correlation ID propagation, where an incoming HTTP header carrying a trace identifier should be automatically placed in MDC for the duration of a request's handling. Could you sketch, in your head, how the pieces you have seen would fit together to implement this? The answer involves a servlet filter or a Spring interceptor to extract the header, MDC manipulation to store the value during the request's lifetime, and perhaps a `BeanPostProcessor` to wrap methods that should propagate the context into asynchronous work. You do not need to build it today, but I hope the mental model feels reachable rather than mysterious, because that shift from mystery to reachability is precisely what mastery of these lifecycle hooks gives you.