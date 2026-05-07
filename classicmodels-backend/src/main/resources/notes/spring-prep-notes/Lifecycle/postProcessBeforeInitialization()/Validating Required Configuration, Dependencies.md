# Validating Required Configuration / Dependencies in `postProcessBeforeInitialization()`

## Why this phase specifically?

At this point in the lifecycle:
- ✅ Constructor has run
- ✅ All dependencies injected (`@Autowired`, setters, `@Value`)
- ✅ `Aware` callbacks completed
- ❌ `@PostConstruct` has **NOT** run yet

This is the **perfect fail-fast checkpoint** — the bean is fully wired, but hasn't started doing any work. If something is misconfigured, you want to crash the application startup *here*, not 3 hours later when a user hits a broken endpoint.

---

## Example 1: Ensure `@Value` properties are not empty

Imagine a service that needs an API key from `application.properties`. If someone forgets to set it, you want startup to fail loudly.

```java
@Service
public class PaymentService {
    @Value("${payment.api-key:}")
    private String apiKey;

    public String getApiKey() { return apiKey; }
}
```

```java
@Component
public class RequiredConfigValidator implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof PaymentService ps) {
            if (ps.getApiKey() == null || ps.getApiKey().isBlank()) {
                throw new BeanInitializationException(
                    "PaymentService requires 'payment.api-key' but it is missing!"
                );
            }
        }
        return bean;
    }
}
```

**What happens:** If `payment.api-key` is missing from properties, the app refuses to start. No surprises at runtime.

---

## Example 2: Custom `@Required` annotation

Enforce that specific fields must be populated — regardless of which bean they live in.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MustBeSet { }
```

```java
@Service
public class EmailService {
    @MustBeSet
    @Value("${smtp.host:}")
    private String smtpHost;
}
```

```java
@Component
public class MustBeSetValidator implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            if (field.isAnnotationPresent(MustBeSet.class)) {
                field.setAccessible(true);
                Object value = field.get(bean);
                if (value == null || (value instanceof String s && s.isBlank())) {
                    throw new BeanInitializationException(
                        "Field '" + field.getName() + "' in bean '" + beanName + "' must be set!"
                    );
                }
            }
        });
        return bean;
    }
}
```

**Why this is powerful:** Works across *any* bean. Just annotate the field, and the validator enforces it everywhere.

---

## Example 3: Validate numeric ranges

Config values that compile fine but are semantically wrong (e.g., a negative thread pool size).

```java
@Service
public class WorkerPool {
    @Value("${worker.threads:0}")
    private int threads;

    public int getThreads() { return threads; }
}
```

```java
@Component
public class WorkerPoolValidator implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof WorkerPool wp) {
            if (wp.getThreads() < 1 || wp.getThreads() > 100) {
                throw new BeanInitializationException(
                    "worker.threads must be between 1 and 100, got: " + wp.getThreads()
                );
            }
        }
        return bean;
    }
}
```

**Why here and not in `@PostConstruct`?** You could put it there — but centralizing validation in a `BeanPostProcessor` means one place enforces rules across *many* beans. It also keeps business logic out of validation concerns.

---

## Example 4: Verify injected dependencies are the right type/variant

Sometimes you inject an interface but want to ensure a specific implementation is wired in production.

```java
@Service
public class OrderService {
    private final PaymentGateway gateway;
    
    public OrderService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
    
    public PaymentGateway getGateway() { return gateway; }
}
```

```java
@Component
@Profile("production")
public class ProductionWiringValidator implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof OrderService os) {
            if (os.getGateway() instanceof MockPaymentGateway) {
                throw new BeanInitializationException(
                    "MockPaymentGateway detected in production profile!"
                );
            }
        }
        return bean;
    }
}
```

**Real-world value:** Prevents the classic "oops, we shipped with the mock" disaster.

---

## Key Takeaways

| Rule | Why |
|------|-----|
| **Fail fast** | A crash at startup is 1000x better than a silent misconfiguration at runtime. |
| **Return the bean unchanged** | Unless you explicitly want to wrap/replace it, always `return bean`. |
| **Don't do heavy work here** | This runs for *every* bean. Keep checks cheap; use `instanceof` guards early. |
| **Prefer custom annotations over type checks** | `@MustBeSet` scales better than hardcoding `instanceof PaymentService`. |
| **Throw `BeanInitializationException`** | Standard Spring exception that halts the context with a clear message. |

The core idea: **`postProcessBeforeInitialization()` is your startup guardian** — it sees every bean after wiring but before it "comes alive," making it the ideal spot to enforce contracts across your entire application.