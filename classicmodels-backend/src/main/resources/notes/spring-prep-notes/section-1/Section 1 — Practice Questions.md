
---

## Section 1 — Practice Questions

---

**Question 1** (Single answer)

A Spring application has the following configuration:

```java
@Configuration
public class AppConfig {

    @Bean
    public PaymentGateway stripeGateway() {
        return new StripePaymentGateway();
    }

    @Bean
    public PaymentGateway paypalGateway() {
        return new PaypalPaymentGateway();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService(stripeGateway());
    }
}
```

A colleague refactors `AppConfig` by adding `@Configuration(proxyBeanMethods = false)`. What is the effect on the `OrderService` bean?

A) No effect — `proxyBeanMethods = false` only affects web-scoped beans  
B) `OrderService` will receive a new `StripePaymentGateway` instance distinct from the `stripeGateway` bean in the container  
C) Spring throws `BeanDefinitionOverrideException` at startup  
D) `OrderService` will fail to start because `stripeGateway()` can no longer be called directly

---

**Question 2** (Single answer)

Given this component:

```java
@Service
public class ReportService {

    @Transactional
    public void generateReport() {
        saveAuditLog(); // internal call
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditLog() {
        // should run in a separate transaction
    }
}
```

What actually happens when `generateReport()` is called from outside the class?

A) `saveAuditLog()` runs in a new separate transaction as declared  
B) `saveAuditLog()` joins the existing transaction from `generateReport()`  
C) `saveAuditLog()` runs without any transaction  
D) Spring throws `IllegalTransactionStateException` at runtime

---

**Question 3** (Multiple answer — Choose 2)

Which two statements correctly describe the difference between `BeanFactoryPostProcessor` and `BeanPostProcessor`?

A) `BeanFactoryPostProcessor` operates on bean instances after they are created  
B) `BeanPostProcessor` operates on bean instances after they are created  
C) `BeanFactoryPostProcessor` fires after all bean definitions are loaded but before any beans are instantiated  
D) `BeanPostProcessor` fires before any bean definitions are loaded  
E) Both `BeanFactoryPostProcessor` and `BeanPostProcessor` can create AOP proxies

---

**Question 4** (Single answer)

A developer writes the following aspect:

```java
@Aspect
@Component
public class TimingAspect {

    @Around("execution(* com.example.service.*.*(..))")
    public void timeMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        joinPoint.proceed();
        System.out.println("Elapsed: " + (System.currentTimeMillis() - start));
    }
}
```

A `@Service` method in `com.example.service` returns an `Order` object. What is the result of calling that method?

A) The method executes normally and the `Order` is returned to the caller  
B) The method executes normally but the caller receives `null`  
C) Spring throws `AdviceReturnTypeMismatchException` at startup  
D) The aspect is ignored because `@Around` must use `AfterReturning` to capture return values

---

**Question 5** (Multiple answer — Choose 3)

Which three of the following will cause `@Transactional` to be silently ignored on a method in a Spring-managed bean?

A) The method is `private`  
B) The method is called from another method in the same class  
C) The method is in a class annotated with `@Service`  
D) The method is `static`  
E) The method is called from a different Spring-managed bean  
F) The bean implements an interface and JDK proxying is in use

---

Take your time — answer all five and I'll explain every option when you're ready.