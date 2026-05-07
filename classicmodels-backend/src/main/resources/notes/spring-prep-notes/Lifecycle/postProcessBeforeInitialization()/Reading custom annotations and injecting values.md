# Reading Custom Annotations and Injecting Values in `postProcessBeforeInitialization()`

## The Big Picture: Why This Matters

Before we write any code, let me help you build the right mental model. You already know Spring has built-in annotations like `@Autowired`, `@Value`, and `@Resource`. Have you ever wondered *how* Spring actually makes those work? The answer is surprisingly satisfying: Spring itself uses `BeanPostProcessor` implementations to process those annotations. For example, `AutowiredAnnotationBeanPostProcessor` is the class that scans your beans for `@Autowired` fields and injects the right dependencies. `CommonAnnotationBeanPostProcessor` does the same for `@Resource` and `@PostConstruct`.

This means that when you write your own `BeanPostProcessor` to handle a custom annotation, you are not doing some exotic hack — you are using the exact same mechanism that Spring uses internally. You are extending Spring using Spring's own extension points, which is a beautiful thing.

Now, why is `postProcessBeforeInitialization()` the right place for this kind of work? Remember the lifecycle order: by the time this phase runs, the constructor has fired and Spring's own dependency injection has completed, but your `@PostConstruct` method has not yet run. That means if your `@PostConstruct` logic needs to *use* the values you injected via your custom annotation, those values must be in place *before* init callbacks execute. This is precisely the window we have.

## Example 1: A Simple `@InjectSystemProperty` Annotation

Let's start with something concrete and easy. Imagine you want to inject Java system properties (like `user.home` or `java.version`) directly into your beans using a custom annotation. Spring's `@Value("${...}")` reads from property files, but what if you want a cleaner way to grab system-level values?

First, we define the annotation itself. Notice that we target fields and retain it at runtime so reflection can see it.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectSystemProperty {
    String value(); // the name of the system property we want
}
```

Next, we use it in a bean. The field is declared but left unassigned because our processor will populate it.

```java
@Service
public class EnvironmentService {

    @InjectSystemProperty("user.home")
    private String userHome;

    @InjectSystemProperty("java.version")
    private String javaVersion;

    @PostConstruct
    public void init() {
        // By the time this runs, our BPP has already set the fields.
        // This is why we chose postProcessBeforeInitialization().
        System.out.println("User home: " + userHome);
        System.out.println("Java version: " + javaVersion);
    }
}
```

Now comes the interesting part — the `BeanPostProcessor` that does the actual work. Read through this carefully because it shows the pattern you will use again and again.

```java
@Component
public class SystemPropertyInjector implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Walk every field declared on the bean's class
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            // Only care about fields marked with our annotation
            InjectSystemProperty annotation = field.getAnnotation(InjectSystemProperty.class);
            if (annotation != null) {
                // Look up the requested system property
                String propertyValue = System.getProperty(annotation.value());

                // Reflection requires us to bypass access checks for private fields
                field.setAccessible(true);
                field.set(bean, propertyValue);
            }
        });
        // Always return the bean — Spring continues using whatever you return
        return bean;
    }
}
```

When the application starts, Spring will call this processor for every bean. For most beans, the loop will find no annotated fields and do nothing. For `EnvironmentService`, it will find the two annotated fields and populate them. By the time `@PostConstruct` fires, the values are already in place. This is exactly the guarantee we wanted.

## Example 2: Injecting Values from an External Configuration Service

Now let's build something more realistic. Imagine your company has a central feature-flag service (or a configuration service like Consul, AWS AppConfig, or a database table) and you want to inject flag values into beans declaratively.

We start again with the annotation. This time we add a default value in case the feature flag is missing, which is a common real-world requirement.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FeatureFlag {
    String name();
    boolean defaultValue() default false;
}
```

Our bean uses the annotation in a natural way. Notice how much cleaner this looks than pulling flags manually from a service inside `@PostConstruct`.

```java
@Service
public class CheckoutService {

    @FeatureFlag(name = "new-checkout-ui", defaultValue = false)
    private boolean useNewUi;

    @FeatureFlag(name = "express-shipping", defaultValue = true)
    private boolean expressShippingEnabled;

    public String process() {
        // Business logic reads these flags naturally, as if they were just fields
        if (useNewUi) return "Rendering new UI";
        return "Rendering old UI";
    }
}
```

The processor is where the magic happens. Notice that this processor itself has a dependency — the `FeatureFlagService` — which Spring injects just like any other bean. A `BeanPostProcessor` is, after all, just a regular Spring bean with a special interface.

```java
@Component
public class FeatureFlagInjector implements BeanPostProcessor {

    private final FeatureFlagService flagService;

    // Constructor injection works on BPPs too — they are regular beans
    public FeatureFlagInjector(FeatureFlagService flagService) {
        this.flagService = flagService;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            FeatureFlag annotation = field.getAnnotation(FeatureFlag.class);
            if (annotation != null) {
                // Ask the remote service for the flag's current value,
                // falling back to the declared default if unavailable
                boolean value = flagService.isEnabled(
                    annotation.name(),
                    annotation.defaultValue()
                );

                field.setAccessible(true);
                field.set(bean, value);
            }
        });
        return bean;
    }
}
```

Pause here and appreciate what we just did. We invented a new way to configure beans — one that looks and feels as native as `@Value` — in about twenty lines of code. Any bean, anywhere in the application, can now declare feature flags as fields, and they will be populated automatically before the bean even starts running.

## Example 3: Type-Aware Injection with Type Conversion

Our examples so far injected strings or booleans. What happens when the target field is an `int` or a `Duration`? This is where you start to see why Spring's real injection machinery is more complex than our toy examples. Let me show you a small, practical version that handles a couple of common types, so you understand the pattern.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectEnvVar {
    String value();
}
```

```java
@Service
public class DatabaseService {

    @InjectEnvVar("DB_MAX_CONNECTIONS")
    private int maxConnections;

    @InjectEnvVar("DB_TIMEOUT_SECONDS")
    private Duration timeout;

    @InjectEnvVar("DB_HOST")
    private String host;
}
```

The processor needs to look at the field's type and convert the raw string value appropriately. This mirrors what Spring does internally — it uses a `ConversionService` to transform strings into target types.

```java
@Component
public class EnvVarInjector implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            InjectEnvVar annotation = field.getAnnotation(InjectEnvVar.class);
            if (annotation == null) return;

            // Environment variables are always strings — conversion is our job
            String rawValue = System.getenv(annotation.value());
            if (rawValue == null) return; // leave field untouched if no value

            // Pick the right conversion based on the declared field type
            Object convertedValue = convert(rawValue, field.getType());

            field.setAccessible(true);
            field.set(bean, convertedValue);
        });
        return bean;
    }

    private Object convert(String raw, Class<?> targetType) {
        // A tiny conversion table — real Spring uses a full ConversionService
        if (targetType == String.class)   return raw;
        if (targetType == int.class)      return Integer.parseInt(raw);
        if (targetType == long.class)     return Long.parseLong(raw);
        if (targetType == boolean.class)  return Boolean.parseBoolean(raw);
        if (targetType == Duration.class) return Duration.ofSeconds(Long.parseLong(raw));
        throw new IllegalArgumentException("Unsupported type: " + targetType);
    }
}
```

This small addition — the `convert` method — turns our annotation from a one-trick pony into something genuinely useful. A quick thought exercise to test your understanding: what would happen if you applied this processor to a field whose type was not in the conversion table, like `BigDecimal`? Take a moment to trace through the code. You would see the `IllegalArgumentException` propagate up, and because this happens during `postProcessBeforeInitialization()`, Spring would abort the entire context startup. That is actually the desired behavior, because it tells you at startup that your annotation is being misused.

## A Few Important Lessons Baked Into These Examples

Let me draw your attention to a few subtle but important points. First, notice that in every example, the processor uses `instanceof` or annotation checks as early-exit guards. This matters because your `BeanPostProcessor` runs for *every single bean in the context*, including Spring's own internal beans, Tomcat beans, DevTools beans, and so on. If you do expensive reflection on every one of them, you will slow down startup noticeably. The guards keep the work cheap for beans that do not care.

Second, notice that `ReflectionUtils.doWithFields()` walks not just the class itself but also its superclasses. This is important because a bean might inherit annotated fields from a base class, and you want to respect those too. If you wrote the loop manually with `getClass().getDeclaredFields()`, you would miss inherited fields.

Third, and this is a question worth sitting with for a moment: why did we always call `field.setAccessible(true)` before setting the value? Because Spring beans typically have `private` fields, and Java's reflection normally refuses to touch private state. The `setAccessible(true)` call bypasses that check. This is fine inside a trusted framework context, but it is the kind of thing you would never do lightly in ordinary application code.

## How This Connects Back to the Lifecycle

Step back and think about where we are in the lifecycle diagram you shared. We are at step 6 in your log output — after Aware callbacks, before `@PostConstruct`. Every technique shown above relies on that exact timing. If we tried the same work in `postProcessAfterInitialization()` (step 10), we would be too late, because the bean's init method would already have run, possibly reading from fields that were still unpopulated. If we tried to do it in the constructor, we would be too early, because our processor would not have had a chance to run yet.

The elegance of `postProcessBeforeInitialization()` is that it is the last moment where you can still shape the bean's state before the bean itself gets a chance to observe that state. This is why Spring placed its own annotation processors at exactly this point, and it is why your custom annotations belong here too.