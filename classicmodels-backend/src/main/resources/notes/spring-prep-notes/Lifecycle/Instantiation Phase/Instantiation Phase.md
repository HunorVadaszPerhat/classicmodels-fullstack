# The Instantiation Phase: Where a Bean Physically Comes Into Existence

## What Actually Happens at This Moment

Before we look at any code, I want you to picture something concrete. When your Spring application starts up, at some point the Java Virtual Machine needs to actually create each bean as a real object in memory. Up until that moment, the bean exists only as a concept. Spring has a class definition, it has rules about how to construct the class, it has knowledge about what dependencies the class needs, but none of that has been turned into an actual object yet. The instantiation phase is the moment where the conceptual bean becomes a physical one. Memory gets allocated, the constructor runs, and when the constructor returns, there is a real object where previously there was only a plan.

This phase is the first step listed in your log output, and it is also the simplest step to describe mechanically. Spring calls the constructor of the bean's class, Java allocates memory for the new object, the constructor body runs, and the result is a freshly constructed object that Spring now holds a reference to. But the simplicity of the mechanics hides some subtlety about what constructors can and cannot do, and understanding that subtlety is what separates developers who write constructors that work well with Spring from those who write constructors that cause frustrating problems.

The critical thing to understand about this phase is what is true and what is not true when your constructor runs. Your constructor is running before almost anything else has happened in the bean's lifecycle. Spring has not yet done any field injection. Spring has not called any Aware methods. Spring has not run any post-processors. The constructor is genuinely the first thing that happens to the object, and every other phase of the lifecycle we have explored happens afterward. This means that the constructor must do its work using only what is passed to it as arguments and what is available as static or literal values. Any field that will be populated by Spring later is not yet populated when the constructor runs, and trying to use such a field inside the constructor will give you either null or a default value, never the real value that will eventually be injected.

Let me walk you through this with examples that will make the mechanics concrete and help you build intuition for what belongs in a constructor and what does not.

## Example One: The Simplest Possible Constructor

Let us begin with a bean that has no constructor written explicitly. Java provides a default constructor for any class that does not declare one, and Spring is perfectly happy to use that default constructor to instantiate the bean. This is the simplest possible case and it is worth seeing clearly before we complicate things.

```java
@Service
public class SimpleGreeter {

    // The class has no explicit constructor. Java silently provides a default
    // no-argument constructor that does nothing beyond the essential work of
    // allocating the object and initializing its fields to their default values.
    // When Spring instantiates this bean, it calls this invisible default constructor.

    @Value("${greeting:Hello}")
    private String greeting;

    public String greet(String name) {
        // When this method runs during the active phase, the greeting field
        // has been populated by Spring's field injection. But that injection
        // happens after the constructor, not during it. At construction time,
        // greeting was null, because the default constructor did nothing to set it.
        return greeting + ", " + name;
    }
}
```

Trace through what happens when Spring instantiates this bean. The JVM allocates memory for a new `SimpleGreeter` object. The default constructor runs, which does nothing explicit but does initialize all fields to their default values, meaning the `greeting` field becomes null. The constructor returns, and Spring now holds a reference to a freshly constructed object whose `greeting` field is still null. Only after this moment does Spring move on to the next phase and inject the actual value from properties into the field. If you had tried to read `greeting` inside the constructor, you would have seen null, because the injection had not happened yet.

This is the first lesson about the instantiation phase: the constructor runs before dependency injection, which means injected values are not available inside the constructor. Many developers discover this the hard way, writing constructors that reference injected fields and then being puzzled when the code produces null pointer exceptions at startup. The rule is simple and unforgiving. Anything a field-injected or setter-injected value will eventually hold is not available inside the constructor, because the injection has not happened yet.

## Example Two: Constructor Injection as the Correct Solution

The limitation we just observed has a beautiful solution, and it is one of the most important patterns in modern Spring development. Instead of relying on field injection, you can declare the bean's dependencies as constructor parameters. Spring will resolve the dependencies and pass them directly to the constructor, which means they are available throughout the constructor's execution. This completely sidesteps the problem of uninitialized fields.

```java
@Service
public class GreeterWithConstructorInjection {

    // The field is declared final, which Java enforces to be set exactly once,
    // in the constructor. This is a quiet but powerful design choice because
    // it makes the bean's state immutable after construction: nothing can ever
    // change the repository reference after the constructor finishes.
    private final GreetingRepository repository;

    // The constructor declares its dependency as a parameter. Spring sees this
    // parameter, finds a bean of matching type in the application context, and
    // passes it to the constructor. The dependency is fully available inside
    // the constructor, unlike field-injected dependencies.
    public GreeterWithConstructorInjection(GreetingRepository repository) {
        // At this moment, the repository parameter holds a reference to a fully
        // constructed and initialized GreetingRepository bean. We can use it
        // immediately, including doing work in the constructor that depends on it.
        this.repository = repository;

        // This line demonstrates that we can actually call methods on the
        // injected dependency inside the constructor, because the dependency
        // is already fully alive. Try doing this with field injection and you
        // will get a null pointer exception, because the field is not yet set.
        System.out.println("Greeter created, repository has "
            + repository.count() + " greetings available");
    }

    public String greet(String name) {
        return repository.getDefaultGreeting() + ", " + name;
    }
}
```

Pause and notice several things about this code because they reveal why constructor injection is almost always the better choice. The field is declared `final`, which means it must be assigned exactly once, in the constructor. Once the constructor returns, the field can never be changed. This is a strong correctness guarantee. It means that any code that reads `repository` after construction is guaranteed to see a valid, non-null reference, because the constructor could not have completed without setting it.

The second thing to notice is that the constructor can actually use the injected dependency. We called `repository.count()` inside the constructor and got a real number back, because Spring handed us a fully functional repository. This is impossible with field injection, where the field is still null when the constructor runs. The ability to use dependencies during construction is a real practical benefit, not just a theoretical one.

The third thing to notice is what happens if the constructor cannot get what it needs. If Spring cannot find a `GreetingRepository` bean to inject, the constructor cannot be called at all, and Spring will refuse to create the bean. The application will fail to start with a clear error message explaining what dependency was missing. Compare this to field injection, where the application might start successfully and then crash much later when someone tries to use the null repository field. Constructor injection turns dependency problems into loud startup failures rather than quiet runtime bugs, which is almost always what you want.

There is one more detail worth mentioning. Since Spring 4.3, if a class has exactly one constructor, you do not need to annotate it with `@Autowired`. Spring figures out that this is the constructor to use and how to inject its parameters. This has become the modern convention, and you will see most new Spring code written this way. Older code might have explicit `@Autowired` annotations on constructors, which still work but are not required for the single-constructor case.

## Example Three: What You Can and Cannot Do In the Constructor

I want to walk through several things you might be tempted to do inside a constructor, showing which are safe and which will cause problems. This is the most practically valuable part of understanding this phase, because knowing the rules lets you write constructors that work correctly rather than constructors that fight against Spring's lifecycle.

```java
@Service
public class ConstructorBoundaries {

    private final DataSource dataSource;

    @Value("${app.name}")
    private String appName;

    private String derivedValue;

    @Autowired
    private MessageService messageService;

    public ConstructorBoundaries(DataSource dataSource) {
        // Safe: we can assign the constructor parameter to a field. This is
        // the fundamental pattern of constructor injection and it always works.
        this.dataSource = dataSource;

        // Safe: we can use the constructor parameter for work inside the
        // constructor. The parameter is a real reference to a real bean.
        int connectionCount = dataSource.getActiveConnections();
        System.out.println("Starting with " + connectionCount + " active connections");

        // Unsafe: the appName field has not been populated yet. Reading it
        // here gives us null, not the actual property value. Many developers
        // write code like this, thinking the @Value annotation has already
        // taken effect, and are surprised when the result is null.
        // this.derivedValue = appName.toUpperCase();  // This would fail with NPE

        // Unsafe: the messageService field is also not populated. Field
        // injection happens after the constructor, so trying to use this
        // field inside the constructor is a mistake.
        // messageService.notify("Starting up");  // This would fail with NPE

        // Safe: we can compute values that depend only on the constructor
        // parameters and on static or literal data. Anything that does not
        // rely on Spring-managed state is fair game.
        this.derivedValue = "prefix-" + dataSource.getUrl();
    }

    @PostConstruct
    public void afterConstruction() {
        // Safe in @PostConstruct: by this point, all field injection is done,
        // so we can use any of the injected fields freely. This is the right
        // place for initialization logic that depends on injected values,
        // which is exactly what we discussed in the @PostConstruct section.
        this.derivedValue = appName.toUpperCase() + "-" + dataSource.getUrl();
        messageService.notify("Component ready");
    }
}
```

I want you to trace through the two failure cases carefully because they represent the most common mistake developers make with constructors in Spring. The commented-out lines would both throw `NullPointerException` because the fields they reference are not yet populated. The `@Value` annotation on `appName` and the `@Autowired` annotation on `messageService` are processed by Spring *after* the constructor returns, which means these fields are still at their default null values when the constructor runs. No amount of thinking about the code will change this timing; it is baked into the order of Spring's lifecycle.

The safe pattern is what we did in the `@PostConstruct` method. By that point, every field has been populated, so you can write logic that freely uses all of them. This is why `@PostConstruct` exists: to give you a place to do initialization that depends on injected state, knowing that the state is actually there. The constructor is too early for this kind of work, which is why you should keep your constructors focused on what they can do safely.

A good mental rule is this. Inside the constructor, you can use constructor parameters and you can use static or literal values. You cannot use fields that will be populated by any form of injection, whether field injection with `@Autowired`, property injection with `@Value`, or setter injection. If your constructor needs something that comes from injection, change the injection to be constructor-based so the value comes in as a parameter instead of being set on a field later.

## Example Four: Multiple Constructors and How Spring Chooses

Sometimes a class has more than one constructor, and you might wonder how Spring decides which one to use. This situation is worth understanding because it comes up in real code, especially when you are adapting an existing class for use as a Spring bean.

```java
@Service
public class MultipleConstructors {

    private final DataSource dataSource;
    private final CacheManager cacheManager;

    // A constructor that takes one parameter. Spring could use this one,
    // but only if we tell it to, because having multiple constructors makes
    // the choice ambiguous.
    public MultipleConstructors(DataSource dataSource) {
        this.dataSource = dataSource;
        this.cacheManager = null;
    }

    // A second constructor that takes two parameters. Spring could also
    // use this one, but again, the ambiguity needs to be resolved.
    public MultipleConstructors(DataSource dataSource, CacheManager cacheManager) {
        this.dataSource = dataSource;
        this.cacheManager = cacheManager;
    }

    // Without any annotation, Spring does not know which constructor to pick
    // and will fail to create the bean. We need to mark one of them as the
    // preferred constructor for Spring to use.
}
```

To resolve the ambiguity, you add `@Autowired` to the constructor you want Spring to use. This tells Spring unambiguously that this is the constructor it should invoke during instantiation.

```java
@Service
public class MultipleConstructorsResolved {

    private final DataSource dataSource;
    private final CacheManager cacheManager;

    public MultipleConstructorsResolved(DataSource dataSource) {
        this.dataSource = dataSource;
        this.cacheManager = null;
    }

    // The @Autowired annotation marks this as the constructor Spring should use.
    // Without it, Spring would not know which of the two constructors to pick,
    // and startup would fail with an error about ambiguous constructors.
    @Autowired
    public MultipleConstructorsResolved(DataSource dataSource, CacheManager cacheManager) {
        this.dataSource = dataSource;
        this.cacheManager = cacheManager;
    }
}
```

The reason this matters is that many real-world classes have multiple constructors for historical or API reasons, and when you bring such a class into a Spring application, you need to tell Spring which constructor represents the fully-dependency-injected form. The single-constructor shortcut that makes `@Autowired` unnecessary applies only when there is one constructor; with multiple constructors, you need to be explicit.

There is also a subtler variant of this situation that is worth knowing about. Sometimes a class has multiple constructors and you want Spring to prefer the one with the most parameters that it can satisfy, falling back to simpler constructors if not all dependencies are available. This is done by annotating the constructor with `@Autowired(required = false)`. This is a more advanced pattern and most applications do not need it, but it exists for situations where dependencies might be optional based on configuration.

## Example Five: Avoiding Heavy Work in the Constructor

A common mistake that is worth addressing directly is putting too much work inside the constructor. The constructor is called once during application startup, and every bean's constructor contributes to the total time before your application is ready to serve traffic. A constructor that does something expensive, like opening a database connection or loading a large file, will delay startup by that amount for every instance of the bean.

```java
@Service
public class HeavyConstructor {

    private final DataSource dataSource;
    private final Map<String, Object> preloadedData;

    public HeavyConstructor(DataSource dataSource) {
        this.dataSource = dataSource;

        // Problematic: this constructor loads a large dataset from the database
        // every time the bean is instantiated. Even though this is a singleton
        // bean that will only be instantiated once, the loading delays startup.
        // Worse, if the dataset is large, the delay can be significant.
        this.preloadedData = new HashMap<>();
        try (ResultSet rs = dataSource.getConnection().createStatement()
                .executeQuery("SELECT key, value FROM config_data")) {
            while (rs.next()) {
                preloadedData.put(rs.getString("key"), rs.getObject("value"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // If this query returns a thousand rows, the constructor takes however
        // long the database takes to return them. If it returns a million rows,
        // the constructor takes much longer, and your application startup is
        // correspondingly slower.
    }
}
```

The better pattern is to keep the constructor fast and defer the heavy work to a later phase, typically `@PostConstruct`, where it can still be done eagerly at startup but is at least separated from the constructor itself. This gives you several benefits: the code is clearer about what is initialization versus what is construction, any failure in the heavy work produces a better error message that points to the initialization logic rather than the constructor, and you have a natural place to add logging or timing measurements around the initialization step.

```java
@Service
public class BetterStartupPattern {

    private final DataSource dataSource;
    private final Map<String, Object> preloadedData = new HashMap<>();

    public BetterStartupPattern(DataSource dataSource) {
        // The constructor is fast. It just captures the dependency and sets
        // up empty state. No work that could fail, no I/O, nothing slow.
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void loadInitialData() {
        // The heavy work is here, in a method that clearly announces itself
        // as doing initialization. It still runs during startup, so the data
        // is ready by the time the bean is active, but it is cleanly separated
        // from the constructor. This separation helps with debugging, testing,
        // and reasoning about the code.
        try (ResultSet rs = dataSource.getConnection().createStatement()
                .executeQuery("SELECT key, value FROM config_data")) {
            while (rs.next()) {
                preloadedData.put(rs.getString("key"), rs.getObject("value"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load initial data", e);
        }
    }
}
```

The general principle is that constructors should be boring. They should capture their dependencies, maybe validate that the dependencies are acceptable, and return quickly. Anything interesting belongs in a later phase where the framework has more context and where errors can be handled more gracefully. This principle applies to almost every object-oriented framework, not just Spring, and following it will make your code easier to understand and easier to debug.

## A Thought to End On

The instantiation phase is the foundation on which the rest of the lifecycle is built. Everything that happens later depends on the object existing, and the constructor is what makes the object exist. But the phase is also more constrained than developers often realize. The constructor runs before dependency injection, before Aware callbacks, before post-processors, before init methods. It is the first moment of the bean's life, and like the first moment of any life, it is a moment of limited capability. The bean exists but has barely anything. Its dependencies are either passed as constructor parameters or they are not yet available at all.

Understanding this constraint is what lets you write constructors that work well with Spring. Use constructor parameters for dependencies, because parameters are available immediately and make the bean's requirements explicit. Avoid referencing fields that will be populated by later injection, because they are not yet populated. Keep the work in the constructor minimal, deferring heavier initialization to `@PostConstruct` where the bean is more complete and where errors are handled more gracefully. Mark fields as final when you can, because this makes the bean immutable after construction and turns a whole class of bugs into compile-time errors.

These are simple rules, but they are also the rules that separate constructors that play well with the rest of the lifecycle from constructors that fight against it. Following them consistently will make your Spring beans easier to write, easier to test, and easier to reason about. And that, really, is the point of understanding any phase of the lifecycle in depth: not to accumulate trivia, but to develop the intuition that lets you write code that works with the framework rather than against it.