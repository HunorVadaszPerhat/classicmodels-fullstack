# Enforcing Architectural Rules in `postProcessBeforeInitialization()`

## Why Architecture Needs Enforcement in the First Place

Before we dive into code, I want you to sit with a problem that every growing codebase eventually faces. On day one of a project, your team agrees on a set of architectural principles. Perhaps you decide that services should never directly access the database, that only repositories may do that. Perhaps you agree that every controller must be annotated with a specific security annotation, or that no public field should ever appear on a Spring-managed bean. These rules feel obvious and easy to follow when the project is small and everyone who writes code is in the same room.

Then the project grows. New developers join. Deadlines tighten. Code reviews get rushed. Six months later, someone finds a service class reaching directly into the database because it was "just a quick fix," and a controller without a security annotation that has been quietly exposing sensitive data. The rules did not disappear. They simply stopped being enforced, because human vigilance is not a reliable enforcement mechanism at scale.

This is where the idea of programmatic architectural enforcement becomes powerful. If your rules are encoded in running code, every violation is caught automatically, every single time, with no reliance on reviewers remembering the guidelines. The `postProcessBeforeInitialization()` hook is an almost perfect place to do this, because it runs once per bean during application startup, it has full reflective access to each bean's class and fields, and it executes before any bean has done any real work. A violation detected here halts the application startup, which is exactly the right response: if your architecture is broken, you want to know immediately, not after the code has already shipped to production.

There is a related technique called ArchUnit, which is a dedicated library for architectural testing. It does similar work but at test time rather than at startup. The two approaches complement each other, and I will mention where each shines as we go. What we are building here is the runtime counterpart, which has the particular strength of catching violations in the exact same environment where your application actually runs.

## Example 1: Forbidding Public Fields on Spring Beans

Let's begin with one of the simplest and most common architectural rules. In a well-designed Spring application, beans should encapsulate their state, which means every field should be private. A public field on a Spring bean is almost always a mistake, because it breaks encapsulation and creates hidden coupling between beans. Let's build a processor that catches this at startup.

```java
@Component
public class NoPublicFieldsEnforcer implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Walk every declared field on the bean's class
        for (Field field : bean.getClass().getDeclaredFields()) {
            // Modifier.isPublic returns true only for fields declared public
            if (Modifier.isPublic(field.getModifiers())) {
                throw new BeanInitializationException(
                    "Architecture violation: bean '" + beanName +
                    "' has public field '" + field.getName() +
                    "'. Fields on Spring beans must be private."
                );
            }
        }
        return bean;
    }
}
```

Trace through what happens when a developer writes a class like this.

```java
@Service
public class BrokenService {
    public String sharedState; // violates our rule
}
```

Spring creates the bean, injects its dependencies, and then hands it to our processor. The processor walks the fields, finds that `sharedState` is public, and throws. The entire application refuses to start, with a clear error message pointing at the exact bean and exact field that violated the rule. The developer sees the error the first time they try to run the code, which is the ideal time to catch it.

Notice something subtle about this design. We are not trying to fix the problem or warn about it politely. We are refusing to let the application start at all. This is a deliberate choice. Architectural rules that merely log warnings tend to accumulate warnings until no one reads them. Architectural rules that prevent startup get fixed immediately, because the developer cannot proceed until the rule is satisfied. This is the same philosophy we saw with fail-fast validation in earlier examples, now applied to architecture.

## Example 2: Enforcing That Services Do Not Depend on Controllers

Here is a rule that comes from the layered architecture pattern. In a typical Spring application, the dependency flow runs downward: controllers depend on services, services depend on repositories, and nothing higher up should depend on anything lower down. If a service depends on a controller, something has gone structurally wrong, because it inverts the intended direction of dependency.

Let's encode this rule in a processor. We will recognize controllers and services by their annotations, which is how Spring itself distinguishes them.

```java
@Component
public class LayeringEnforcer implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // We only care about services for this particular rule
        if (!bean.getClass().isAnnotationPresent(Service.class)) {
            return bean;
        }

        // Look at every field in the service to see what it depends on
        for (Field field : bean.getClass().getDeclaredFields()) {
            Class<?> fieldType = field.getType();

            // If the field's type is annotated @Controller,
            // this service is depending on a controller, which is forbidden
            if (fieldType.isAnnotationPresent(Controller.class) ||
                fieldType.isAnnotationPresent(RestController.class)) {

                throw new BeanInitializationException(
                    "Layering violation: service '" + beanName +
                    "' depends on controller '" + fieldType.getSimpleName() +
                    "'. Services must not depend on controllers."
                );
            }
        }
        return bean;
    }
}
```

The value of encoding this as a runtime check is that it catches the violation regardless of how the dependency was introduced. Maybe the developer wrote an `@Autowired` field directly. Maybe they used constructor injection. Maybe they used a setter. In all cases, the field ends up on the class and the processor sees it. The rule is enforced uniformly, which is much harder to achieve with code review alone.

Pause here and think about an edge case. What happens if the service does not depend on the controller directly but on an interface that the controller implements? The field's declared type would be the interface, not the controller class, and our processor would not catch the violation. This is a genuine limitation, and it illustrates an important principle of architectural enforcement: your rules can only see the static structure of the code, not the runtime wiring. For deeper checks, you would need to inspect the actual injected object, which becomes more fragile. In practice, most teams accept this limitation because direct dependencies on controller classes are already the most common way the rule gets broken, and catching ninety percent of violations reliably is far better than trying to catch everything and getting it subtly wrong.

## Example 3: Requiring Repositories to Follow a Naming Convention

Naming conventions are another classic category of architectural rule. Suppose your team has agreed that every repository interface must end in the word `Repository`, so that anyone reading the code can instantly tell what a class is for by its name. The rule is enforced by convention today, which means a tired developer on a Friday afternoon can easily slip a class named `UserDao` into the codebase and no one notices.

We can enforce the rule in a processor that checks every bean annotated with `@Repository`.

```java
@Component
public class RepositoryNamingEnforcer implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Only inspect beans marked as repositories
        if (!bean.getClass().isAnnotationPresent(Repository.class)) {
            return bean;
        }

        String className = bean.getClass().getSimpleName();

        // The naming rule: class name must end with "Repository"
        if (!className.endsWith("Repository")) {
            throw new BeanInitializationException(
                "Naming violation: repository class '" + className +
                "' must end with 'Repository'. Rename it to follow the convention."
            );
        }
        return bean;
    }
}
```

A class like the following would cause startup to fail.

```java
@Repository
public class UserDao { // violates the naming convention
    // ...
}
```

Notice that we are checking the simple class name rather than the bean name. The bean name in Spring is usually derived from the class name but can be overridden, so the class name is the more reliable signal for a naming convention about code. This is a small detail, but it matters when you are deciding exactly what your architectural rule should measure.

There is a thoughtful counterpoint worth considering here. Some teams prefer to enforce naming conventions with ArchUnit tests rather than runtime processors, because naming mistakes are static structural issues that do not need to be checked every time the application starts. This is a reasonable perspective. The benefit of doing it in a processor is that it runs in every environment automatically, including when you run the application locally or deploy it, without anyone having to remember to run the architectural tests. The cost is a small amount of additional startup work. Neither answer is universally right, and a mature team often uses both approaches depending on the rule.

## Example 4: Requiring Controllers to Be Annotated with a Security Annotation

This is the kind of rule that has real security implications. Suppose your application uses Spring Security and every controller method must be explicitly annotated with either `@PreAuthorize` or `@PermitAll` to declare who can call it. A method with neither annotation represents an ambiguity that should never reach production. Let's encode this as an architectural rule.

First, imagine the annotations that might be used.

```java
// Existing Spring Security annotation, imagined here as our rule
// @PreAuthorize("hasRole('USER')")

// And a custom marker for methods intentionally open to everyone
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PermitAll {
}
```

Now the processor. It walks every method on every controller and checks that each one has either annotation. Methods inherited from `Object` (like `toString` or `hashCode`) are excluded because they are not real endpoints.

```java
@Component
public class ControllerSecurityEnforcer implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();

        // Only inspect controllers
        if (!beanClass.isAnnotationPresent(RestController.class) &&
            !beanClass.isAnnotationPresent(Controller.class)) {
            return bean;
        }

        // Walk the methods declared on this controller
        for (Method method : beanClass.getDeclaredMethods()) {
            // Skip methods that aren't HTTP endpoints
            if (!isEndpointMethod(method)) continue;

            boolean hasSecurityAnnotation =
                method.isAnnotationPresent(PreAuthorize.class) ||
                method.isAnnotationPresent(PermitAll.class);

            if (!hasSecurityAnnotation) {
                throw new BeanInitializationException(
                    "Security violation: controller method '" +
                    beanClass.getSimpleName() + "." + method.getName() +
                    "' has no @PreAuthorize or @PermitAll annotation. " +
                    "Every endpoint must declare its access policy."
                );
            }
        }
        return bean;
    }

    // Recognize methods that correspond to HTTP endpoints
    // by checking for Spring's mapping annotations
    private boolean isEndpointMethod(Method method) {
        return method.isAnnotationPresent(GetMapping.class) ||
               method.isAnnotationPresent(PostMapping.class) ||
               method.isAnnotationPresent(PutMapping.class) ||
               method.isAnnotationPresent(DeleteMapping.class) ||
               method.isAnnotationPresent(RequestMapping.class);
    }
}
```

Take a moment to appreciate what this rule gives you in a real security-conscious team. The most common way that security holes enter a web application is not through sophisticated attacks but through developers accidentally forgetting to add an access control check to a new endpoint. Our processor makes that particular mistake impossible, because the application will refuse to start if any endpoint is missing its policy annotation. Instead of relying on code review to catch the omission, we have converted the rule into a mechanical check that runs the same way every time. This is exactly the kind of place where programmatic enforcement pays for itself many times over.

## Example 5: Combining Multiple Rules into a Single Processor

As your list of architectural rules grows, you might wonder whether to write one processor per rule or to combine them. Both are legitimate designs, but I want to show you the combined version because it lets us discuss a subtle lifecycle consideration. Let's build a single processor that runs several checks in sequence.

```java
@Component
public class ArchitectureGuardian implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Collect all violations before failing, so the developer sees everything at once
        List<String> violations = new ArrayList<>();

        checkNoPublicFields(bean, beanName, violations);
        checkRepositoryNaming(bean, beanName, violations);
        checkControllerSecurity(bean, beanName, violations);

        if (!violations.isEmpty()) {
            // Join all violations into a single readable message
            String message = "Architecture violations in bean '" + beanName + "':\n - "
                + String.join("\n - ", violations);
            throw new BeanInitializationException(message);
        }
        return bean;
    }

    private void checkNoPublicFields(Object bean, String beanName, List<String> violations) {
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                violations.add("public field '" + field.getName() + "' is not allowed");
            }
        }
    }

    private void checkRepositoryNaming(Object bean, String beanName, List<String> violations) {
        if (bean.getClass().isAnnotationPresent(Repository.class)
            && !bean.getClass().getSimpleName().endsWith("Repository")) {
            violations.add("repository class name must end with 'Repository'");
        }
    }

    private void checkControllerSecurity(Object bean, String beanName, List<String> violations) {
        // (abbreviated for readability; same logic as Example 4)
    }
}
```

There are two subtle points worth pausing on here. The first is that we accumulate violations rather than throwing on the first one. When a developer finally runs the application after a long refactor, they will probably have broken three or four rules, not just one. Reporting them one at a time is frustrating, because each fix triggers another failed startup. Collecting them all and reporting them together lets the developer fix everything in one pass, which is a far better experience. Small design choices like this are what separate frameworks that feel helpful from frameworks that feel punishing.

The second point is about performance. A `BeanPostProcessor` runs for every bean in the context, which in a large application might be hundreds or thousands of beans. Reflection is not free, so you want your checks to be as cheap as possible. The early-exit pattern you saw in earlier examples, where we immediately return if the bean is not the kind we care about, becomes especially important here. If you have ten architectural rules and each one does a full reflective walk of every bean, your startup time will suffer. A single processor that walks the fields once and runs all its checks inside that single walk is dramatically more efficient than ten separate processors each doing their own walks.

## When This Approach Is and Is Not the Right Tool

I want to close with a balanced perspective, because architectural enforcement is powerful but not infinite in scope. Let's think about where this technique shines and where it does not.

It shines when the rule can be checked by looking at a single bean in isolation, when violations should halt the system rather than merely be recorded, and when the rule is something the team actively wants to enforce rather than merely measure. The examples we walked through all fit this profile. A public field is observable on a single bean. A missing security annotation is observable on a single controller. A repository without the correct suffix is observable on a single class. In each case, we can make a definitive judgment at the moment the bean is being initialized.

It does not shine when the rule requires cross-bean analysis, such as "no two beans may share the same database table" or "every event publisher must have at least one subscriber." These rules require a global view of the application that you cannot construct during the per-bean initialization callback. For these, you would need either a post-startup inspection using `ApplicationReadyEvent` or a dedicated architecture testing framework like ArchUnit that analyzes the compiled class files directly. It also does not shine when the violation is subjective or culturally dependent, such as "method names should be descriptive." Rules that a computer cannot mechanically check are rules that belong in a style guide and in code review, not in a processor.

The thought I want to leave you with is this. Architecture in a growing codebase is like a garden. It takes continuous attention to stay healthy, and the moment you stop tending it, weeds creep in. A `BeanPostProcessor` that enforces rules at startup is a kind of automated gardener, patiently checking every bean every time the application starts and refusing to let architectural weeds take root. The rules you encode this way become a permanent, self-enforcing property of your system, which is a remarkably durable form of engineering discipline. You now have the tools to build that gardener yourself, and the lifecycle understanding to know exactly why `postProcessBeforeInitialization()` is the right moment for it to do its work.