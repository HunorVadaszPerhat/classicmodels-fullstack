SYSTEM READY - ENDLESS RANDOM MODE INITIATED

Q1 — Config: Full (@Configuration/CGLIB) vs Lite (@Component) mode — Level 2 — Single Answer: Choose 1

```java
@Component
public class AppConfig {

    @Bean
    public PaymentGateway paymentGateway() {
        return new StripePaymentGateway();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService(paymentGateway()); 
    }

    @Bean
    public AuditService auditService() {
        return new AuditService(paymentGateway()); 
    }
}
```
***
Assuming a standard `AnnotationConfigApplicationContext` is fully initialized with `AppConfig`, how many total instances of `StripePaymentGateway` will be instantiated during startup?

A) Exactly 1 instance, as Spring automatically proxies all `@Bean` methods to enforce singleton scope.

B) Exactly 2 instances, one injected into `OrderService` and one injected into `AuditService`.

C) Exactly 3 instances: one registered in the container, plus two created manually via direct method calls.

D) 0 instances. Throws an exception on startup because `@Bean` cannot be declared inside a `@Component` class.

***

Q1 RESULT: A ❌ INCORRECT

A) ❌ — Spring only uses CGLIB to enforce singleton scope for inter-bean calls when the class is annotated with `@Configuration` (Full mode), not `@Component` (Lite mode).

B) ❌ — This ignores the initial bean creation by the container itself, which still processes the `paymentGateway()` method once to register it in the context.

C) ✅ — The container calls `paymentGateway()` once to register the bean, and the plain Java method calls inside `orderService` and `auditService` each create a new unmanaged instance.

D) ❌ — `@Bean` methods are perfectly valid inside `@Component` classes, they simply operate in "Lite" mode without proxy interceptors.

📋 Key Mechanism: "Lite" Configuration Mode vs "Full" Configuration Mode (CGLIB Proxying).
📋 Reference: Objective 1.2.1 (The CGLIB proxy).
📋 Modern Delta: Spring Framework 5.2+ allows `@Configuration(proxyBeanMethods = false)` to explicitly enforce this exact "Lite" mode behavior for faster startup.

*Total Score:* 0/1 | *Current Weakness:* Config: Full (@Configuration/CGLIB) vs Lite (@Component) mode

***