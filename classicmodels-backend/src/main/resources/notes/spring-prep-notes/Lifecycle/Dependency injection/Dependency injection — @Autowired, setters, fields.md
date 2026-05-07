# The Dependency Injection Phase: How Beans Get Their Collaborators

## Understanding Why Dependency Injection Exists At All

Before we look at any code, I want to spend a moment building up the idea of dependency injection itself, because the lifecycle phase makes much more sense once you understand why this pattern exists and what problem it solves. Imagine you are writing a class that needs to send emails. The class needs access to an email service to do its work. You have a few options for how this class gets that email service. You could have the class create the email service itself, perhaps by calling something like `new EmailService()` in its constructor. You could have the class look up the email service from some global registry, perhaps by calling something like `ServiceLocator.get(EmailService.class)`. Or you could have the class declare that it needs an email service and trust that someone else will provide it.

That third approach is dependency injection, and the reasons it wins over the alternatives are worth understanding genuinely rather than just accepting on faith. When a class creates its own dependencies, it becomes tightly coupled to the specific implementations it creates, which makes it hard to test with different implementations and hard to change when requirements evolve. When a class looks up its dependencies from a registry, the dependencies become invisible in the class's API, which makes it hard to understand what the class needs without reading its implementation. When a class declares its dependencies and lets someone else provide them, the dependencies are explicit, flexible, and easy to substitute for testing. This clarity about what a class needs, combined with the flexibility to provide different implementations in different contexts, is the foundation of why dependency injection has become the dominant pattern in modern Java development.

Spring is essentially a sophisticated tool for doing dependency injection. When you annotate a class with `@Service` or `@Component`, you are telling Spring that this class should be available as a bean in the application context, meaning Spring will manage its lifecycle and provide it to other beans that need it. When another class declares that it needs your service, Spring looks at what is available, finds your service, and provides it. The entire purpose of the dependency injection phase in the lifecycle is to do this providing. Spring walks through the bean's dependencies, finds the right beans to satisfy each one, and hands them over.

Understanding this phase deeply means understanding three different ways that Spring can do the handing over, each with its own characteristics and trade-offs. These three ways are constructor injection, setter injection, and field injection. Your log output showed the bean receiving values via setter injection, which is why the log line called out `[Setter injection]` explicitly. But setter injection is just one of three mechanisms, and understanding all three is what lets you make good choices about how your beans get their dependencies. Let me walk you through each one in turn, building up your mental model so that by the end of this section, the trade-offs between them will feel natural to you.

## The First Mechanism: Constructor Injection

Constructor injection is the approach we already started exploring in the previous section about the instantiation phase. The bean declares its dependencies as parameters on its constructor, and Spring passes the dependencies in when it calls the constructor. This mechanism is actually a bit of a special case relative to the other two, because constructor injection happens *during* instantiation rather than after it. The dependencies arrive at the same moment the bean comes into existence, which is why it behaves differently from the other injection mechanisms.

Let me show you what this looks like in concrete code, with enough detail that you can see exactly what Spring is doing behind the scenes.

```java
public interface EmailService {
    void sendEmail(String to, String subject, String body);
}

@Service
public class SmtpEmailService implements EmailService {
    @Override
    public void sendEmail(String to, String subject, String body) {
        System.out.println("Sending email to " + to);
    }
}

@Service
public class NotificationService {

    // The field is declared final, which tells Java that this field must be
    // assigned exactly once, in the constructor. Once assigned, it can never
    // change. This is a powerful correctness guarantee that only works with
    // constructor injection, because the other injection mechanisms happen
    // after construction and therefore cannot assign to final fields.
    private final EmailService emailService;

    // Since Spring 4.3, if a class has exactly one constructor, Spring
    // automatically uses it for injection without needing @Autowired.
    // The constructor parameter tells Spring what dependency to provide.
    public NotificationService(EmailService emailService) {
        // At this exact moment, the emailService parameter holds a reference
        // to a real, fully-initialized EmailService bean. Spring looked up
        // the dependency before calling the constructor and passed it in.
        this.emailService = emailService;

        // Because the dependency is already available inside the constructor,
        // we can use it right away if we want to. This is a genuine capability
        // that the other injection mechanisms do not provide, because they
        // populate fields after the constructor has already finished running.
        System.out.println("NotificationService created with email service: "
            + emailService.getClass().getSimpleName());
    }

    public void notifyUser(String user, String message) {
        // During the active phase, we use the injected dependency to do our work.
        emailService.sendEmail(user, "Notification", message);
    }
}
```

Walk through what happens when Spring creates this bean. Before Spring can call the `NotificationService` constructor, it needs to have something to pass as the `emailService` parameter. Spring looks through its application context for a bean that implements the `EmailService` interface, finds the `SmtpEmailService`, and uses that as the argument. If Spring cannot find a suitable bean, it throws an exception and startup fails. If everything checks out, Spring calls the constructor with the resolved dependency, the constructor body runs with full access to the dependency, and the newly-constructed bean has all its dependencies in place by the time construction completes.

I want to draw your attention to something subtle that makes this mechanism particularly valuable. The `emailService` field is declared `final`, which is a Java keyword that tells the compiler "this field must be assigned in the constructor and can never be changed afterward." This has two important consequences. First, the compiler enforces that the field gets assigned, which means it is impossible to have a bean that somehow has a null `emailService` after construction. Second, the field can never be changed after construction, which means any method on this bean can trust that the field holds a valid reference every time it looks at it. No defensive null checks, no worry about concurrent modification, no ambiguity about the field's state.

This immutability is genuinely valuable, and it is only achievable through constructor injection. The other injection mechanisms we will look at assign to fields after construction, which means those fields cannot be final. Constructor injection is the only mechanism that lets you write truly immutable beans, and immutable beans are easier to reason about, easier to test, and safer to use in concurrent contexts. This is one of the strongest arguments for preferring constructor injection as your default choice in modern Spring code.

## The Second Mechanism: Setter Injection

Setter injection is what your original log output showed happening to the lifecycle bean. The name tells you what it does: Spring populates the bean's dependencies by calling setter methods after the constructor has finished. This is the mechanism that Spring's designers originally emphasized in early versions of the framework, and it is still useful in specific situations, though it has fallen out of fashion as the default choice.

Let me show you what this looks like in code, and then we can discuss what makes it different from constructor injection.

```java
@Service
public class ReportGenerator {

    // The field is NOT final, because it needs to be assignable by the setter
    // method that Spring will call later. This is the first visible consequence
    // of using setter injection: you lose the immutability guarantee that
    // constructor injection provides.
    private EmailService emailService;

    // The default constructor, either explicit like this or silently provided
    // by Java, runs first. At this moment, emailService is still null, because
    // the setter has not been called yet. Any code in the constructor that
    // tried to use emailService would fail with NullPointerException.
    public ReportGenerator() {
        // Notice that we cannot use emailService here, even though we know
        // Spring will eventually provide it. The timing simply does not allow
        // it: this constructor runs before any setter injection happens.
        System.out.println("ReportGenerator constructed, but emailService is: "
            + emailService);  // This will print "null"
    }

    // The setter method itself. The @Autowired annotation tells Spring that
    // this setter should be called during the dependency injection phase,
    // with a matching bean provided as the argument.
    @Autowired
    public void setEmailService(EmailService emailService) {
        // Spring calls this method after the constructor has returned.
        // The emailService parameter is a real, fully-initialized bean.
        this.emailService = emailService;
        System.out.println("Setter injection completed, emailService is: "
            + emailService.getClass().getSimpleName());
    }

    public void generateReport(String recipient) {
        // During the active phase, we use the injected dependency. By this
        // point, the setter has been called and the field is populated.
        emailService.sendEmail(recipient, "Report", "Here is your report");
    }
}
```

Trace through the timeline of what happens when Spring creates this bean. Spring calls the no-argument constructor, which runs with `emailService` still at its default null value. The constructor prints "null," demonstrating that the field really is not yet populated. The constructor returns, and the bean exists but is incomplete. Spring then moves on to the dependency injection phase, finds the `@Autowired` annotation on the setter, looks up a matching bean, and calls the setter with the resolved dependency. After the setter returns, the field is populated and the bean is ready for the next phases of the lifecycle.

There are two things worth noticing about this pattern, and both of them explain why setter injection is less popular in modern code than it used to be. First, the field cannot be final, because final fields can only be assigned in the constructor, and setter injection assigns after construction. This means the bean's state is mutable, and any code that uses the bean has to consider the possibility that the field might be null if somehow the injection did not happen or did not complete. Second, there is a window between construction and setter injection during which the bean exists but is incomplete. If anything tried to use the bean during that window, it would fail.

For most applications, these concerns push developers toward constructor injection as the default. But setter injection has legitimate uses, especially for optional dependencies or for circular dependencies that cannot be resolved any other way. Let me show you the optional dependency case, because it is the most common legitimate reason to reach for setter injection.

```java
@Service
public class FlexibleReportGenerator {

    private final EmailService emailService;  // Required, always needed
    private AuditLogger auditLogger;  // Optional, may or may not be present

    // Constructor injection for the required dependency. This is non-negotiable:
    // we cannot function without an EmailService, so Spring must provide one
    // or refuse to create the bean at all.
    public FlexibleReportGenerator(EmailService emailService) {
        this.emailService = emailService;
    }

    // Setter injection for the optional dependency. The required = false flag
    // tells Spring that this setter only needs to be called if a matching
    // bean is available. If no AuditLogger exists in the context, Spring
    // simply does not call the setter, and the field remains null.
    @Autowired(required = false)
    public void setAuditLogger(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public void generateReport(String recipient) {
        // The required dependency is always safe to use, because the bean
        // could not have been created without it.
        emailService.sendEmail(recipient, "Report", "Here is your report");

        // The optional dependency needs a null check, because the setter
        // might not have been called if no matching bean was available.
        // This explicit null check makes the optionality visible in the code.
        if (auditLogger != null) {
            auditLogger.logReportGeneration(recipient);
        }
    }
}
```

This pattern of mixing constructor injection for required dependencies with setter injection for optional ones gives you the best of both worlds. The required dependencies are guaranteed to be present, thanks to constructor injection, and the bean simply cannot exist in a broken state. The optional dependencies are expressed through setters with `required = false`, which makes their optionality explicit and gives you a clear hook for Spring to populate them when they are available. This is the approach that most modern Spring applications use when they genuinely need optional dependencies.

There is another situation where setter injection remains necessary, and that is when two beans have a circular dependency on each other. If bean A needs bean B and bean B needs bean A, Spring cannot satisfy both through constructor injection, because neither constructor can run until the other is already finished. Setter injection can break this deadlock, because the constructors can run first with empty fields, and then Spring can call the setters afterward to complete the wiring. Circular dependencies are generally considered a design smell and should be refactored when possible, but when you genuinely need them, setter injection is one way to make them work.

## The Third Mechanism: Field Injection

Field injection is the third and final way Spring can provide dependencies. The name again tells you what it does: Spring uses reflection to directly set the value of a field marked with `@Autowired`, without going through a constructor parameter or a setter method. This is the most concise way to write dependency injection, and for a long time it was popular because of that conciseness. But modern Spring practice has moved away from field injection for reasons that are worth understanding in detail.

```java
@Service
public class FieldInjectionExample {

    // The @Autowired annotation on the field tells Spring to populate this
    // field directly via reflection. No setter method is needed. Spring
    // bypasses normal Java access rules to write the value into the field.
    @Autowired
    private EmailService emailService;

    // Another field, another @Autowired annotation. The pattern scales
    // simply: just add an annotation and declare a field.
    @Autowired
    private AuditLogger auditLogger;

    public FieldInjectionExample() {
        // As with setter injection, the constructor runs before any fields
        // are populated. Attempting to use emailService here would produce
        // a NullPointerException, because the field is still at its default
        // null value.
        System.out.println("Constructor running, emailService is: " + emailService);
    }

    public void doWork() {
        // By the time any business method runs, the fields have been
        // populated. Reading them at this point returns the real dependencies.
        emailService.sendEmail("user@example.com", "Subject", "Body");
        auditLogger.logActivity("Work done");
    }
}
```

On the surface, this code looks wonderfully concise. Two annotations and two field declarations, and you have two injected dependencies. No constructor parameters, no setter methods, no boilerplate. This conciseness was genuinely appealing when the pattern first became popular, and a lot of Spring code written in the early 2010s uses field injection heavily. So why has the community moved away from it?

The reasons are worth walking through carefully, because they illustrate some real limitations of the pattern that are not immediately obvious. The first reason is that field-injected dependencies cannot be final. Just like with setter injection, the field has to be mutable because the assignment happens after construction. This means you lose the immutability guarantee that constructor injection provides, with all the correctness benefits that immutability brings.

The second reason is more subtle and has to do with testability. When you want to test a class in isolation, you typically need to provide it with test implementations of its dependencies, often mock objects. With constructor injection, this is straightforward: you call the constructor with your mock objects, and the bean is ready for testing. With field injection, you have no constructor to call with mocks, because Spring uses reflection to populate the fields. To test a field-injected class without Spring, you either need to use reflection yourself to set the fields, or you need to use a testing framework like Mockito with its `@InjectMocks` annotation that does the reflection for you. Both options are more complex and more fragile than a simple constructor call.

```java
// Testing a constructor-injected class: straightforward, no framework magic needed.
class NotificationServiceTest {
    @Test
    void testNotifyUser() {
        // We create a mock email service, pass it to the constructor, and test.
        // The test is a plain Java test, requires no reflection, and clearly
        // shows what dependencies the class under test needs.
        EmailService mockEmailService = mock(EmailService.class);
        NotificationService service = new NotificationService(mockEmailService);

        service.notifyUser("alice", "Hello");

        verify(mockEmailService).sendEmail("alice", "Notification", "Hello");
    }
}

// Testing a field-injected class: requires reflection or a testing framework.
class FieldInjectionExampleTest {
    @Test
    void testDoWork() {
        // We have to either use reflection to set the fields, or use a
        // framework like Mockito to handle the injection for us. Either
        // way, the test is more complex than it needs to be.
        FieldInjectionExample example = new FieldInjectionExample();

        // Option one: manual reflection, which is verbose and error-prone.
        Field emailField = FieldInjectionExample.class.getDeclaredField("emailService");
        emailField.setAccessible(true);
        emailField.set(example, mock(EmailService.class));

        // The test logic follows, but the setup is already more complex.
    }
}
```

The third reason to avoid field injection is that it hides the class's dependencies. When you look at a field-injected class, the dependencies are scattered throughout the class body rather than being collected in a single prominent location like a constructor signature. A constructor with five parameters visibly signals that the class has five dependencies, which is useful information. A class with five field-injected dependencies requires you to scan the whole class to find them all. This visibility matters for maintainability, because it makes it easier to notice when a class has too many dependencies and should be refactored into smaller components.

The fourth reason is that field injection requires the application to be running inside a dependency injection framework that supports it. With constructor injection, your class is just a plain Java class with a normal constructor, usable anywhere. With field injection, your class only works when something populates its annotated fields, which means it is tied to Spring or a similar framework. This coupling is usually acceptable in a Spring application, but it makes your classes less portable and less usable in contexts where you might want to use them without the full framework.

For all these reasons, the modern recommendation is to avoid field injection in new code and to use constructor injection as the default. Field injection is not deprecated and still works, but the consensus across the Spring community is that its concise syntax is not worth the trade-offs in immutability, testability, and clarity.

## How Spring Decides What To Inject

A question that often comes up once you understand the three injection mechanisms is how Spring figures out which bean to provide for each dependency. The answer reveals something about how Spring's application context works, and it is worth understanding so that you can diagnose problems when Spring cannot find a suitable bean or finds multiple candidates.

When Spring sees a dependency of a particular type, whether as a constructor parameter, a setter parameter, or an annotated field, it looks through all the beans in the application context for ones that match. The match is based primarily on type: any bean whose class is assignable to the dependency's type is a candidate. If there is exactly one candidate, Spring uses it. If there are zero candidates, Spring throws an exception and startup fails, unless the dependency is marked as optional with something like `@Autowired(required = false)`. If there are multiple candidates, Spring needs additional help to decide which one to use.

The additional help comes in the form of qualifiers. The most common qualifier is `@Qualifier`, which lets you name a specific bean and inject it by name rather than just by type.

```java
@Service
public class PaymentController {

    private final PaymentProcessor primaryProcessor;
    private final PaymentProcessor backupProcessor;

    // When multiple beans implement the same interface, we use @Qualifier
    // to tell Spring exactly which one to inject. The qualifier strings
    // match the bean names defined elsewhere in the configuration.
    public PaymentController(
            @Qualifier("stripeProcessor") PaymentProcessor primaryProcessor,
            @Qualifier("paypalProcessor") PaymentProcessor backupProcessor) {
        this.primaryProcessor = primaryProcessor;
        this.backupProcessor = backupProcessor;
    }
}
```

This mechanism is essential when you have multiple implementations of an interface and need to be explicit about which implementation goes where. Without the qualifier, Spring would not know which `PaymentProcessor` to inject for each parameter, because both constructor parameters have the same type. With the qualifier, each parameter is uniquely identified by the bean name of the implementation it should receive.

Another common pattern for handling multiple candidates is marking one of them as the primary choice with `@Primary`. This is useful when you want one implementation to be the default that most beans receive, with specific beans overriding the default only when needed.

```java
@Service
@Primary
public class StripeProcessor implements PaymentProcessor {
    // This is the default PaymentProcessor, used when no qualifier is specified.
}

@Service
public class PaypalProcessor implements PaymentProcessor {
    // This implementation is only used when explicitly requested by name.
}

@Service
public class SimplePaymentService {

    private final PaymentProcessor processor;

    // No qualifier needed. Spring picks the @Primary bean, which is Stripe.
    public SimplePaymentService(PaymentProcessor processor) {
        this.processor = processor;
    }
}
```

These mechanisms for resolving ambiguity, the qualifier annotation and the primary annotation, give you precise control over which beans get injected where. You do not always need them; many applications have only one implementation of each type and can rely on simple type-based matching. But when you do need them, they are the tools that let you manage complex dependency graphs without letting Spring get confused.

## A Practical Recommendation for Your Own Code

Let me close with a recommendation about how to actually use this knowledge in your own work, because the three mechanisms are not equally appropriate for different situations. My suggestion, based on what the Spring community has converged on over many years of experience, is to treat constructor injection as your default and to reach for the other mechanisms only when you have a specific reason.

Use constructor injection for required dependencies, which is almost all dependencies in most beans. Mark the fields as final, so that Java enforces immutability. Write a single constructor that takes all the required dependencies as parameters, and let Spring automatically detect it as the injection point. This gives you immutable beans, clear dependency declarations, easy testability, and beans that work correctly in any context, not just when Spring is managing them.

Use setter injection for the rare cases where a dependency is genuinely optional and your bean can function correctly without it. Combine this with constructor injection for the required dependencies on the same bean, so you get the benefits of both patterns where each applies best.

Avoid field injection in new code you write, even though it is syntactically concise. The trade-offs are real, and the conciseness is not worth them in most cases. If you inherit a codebase that uses field injection heavily, you do not necessarily need to refactor it all at once, because the mechanism works correctly and does not cause bugs by itself. But new code should use constructor injection, and when you touch old code for other reasons, migrating it to constructor injection is a low-risk improvement that pays dividends in maintainability.

Following these guidelines will give you beans that play well with Spring's lifecycle, that are easy to test and reason about, and that avoid most of the subtle problems that can arise when dependency injection is used carelessly. The dependency injection phase is one of the most important phases of the lifecycle, because it is where the connections between your beans actually get made. Understanding how to participate in this phase well is one of the most practical skills you can develop as a Spring developer.