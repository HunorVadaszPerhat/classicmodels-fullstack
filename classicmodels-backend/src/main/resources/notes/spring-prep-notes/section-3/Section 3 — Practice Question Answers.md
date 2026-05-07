## Section 3 — Practice Question Answers

---

**Question 1 — Answer: C**

`@SpringBootApplication` is composed of exactly three annotations: `@Configuration` (marks the class as a bean definition source), `@EnableAutoConfiguration` (activates Boot's auto-configuration mechanism), and `@ComponentScan` (scans the package and sub-packages for components). This is a direct factual question the exam tests frequently — know these three cold.

- **A** — Wrong. `@EnableTransactionManagement` is not part of `@SpringBootApplication`. It's a separate annotation activated by auto-configuration when needed.
- **B** — Wrong. `@Component` is not one of the three. The correct annotation is `@Configuration` — the main class is a configuration source, not just a component.
- **D** — Wrong. `@EnableAspectJAutoProxy` is not part of `@SpringBootApplication`. Like `@EnableTransactionManagement`, it's activated separately by auto-configuration.

---

**Question 2 — Answer: C**

When a method returns a plain object type (not `ResponseEntity`), Spring assumes 200 OK for normal returns. If an unchecked exception propagates out of the handler method with no `@ExceptionHandler` to catch it, `DefaultHandlerExceptionResolver` doesn't know about custom application exceptions — it falls through to Spring's default behaviour, which is 500 Internal Server Error. The exception is unhandled so the container treats it as a server error.

- **A** — Wrong. 404 requires either `ResponseEntity.notFound()`, `@ResponseStatus(NOT_FOUND)` on the exception class, or an `@ExceptionHandler` that returns 404. An unchecked exception with no handler does not automatically become a 404.
- **B** — Wrong. 400 is for client errors like validation failures (`MethodArgumentNotValidException`). An arbitrary unchecked exception doesn't map to 400.
- **D** — Wrong. A plain return type produces 200 OK only when the method returns normally. An exception propagating out of the method is not a normal return.

---

**Question 3 — Answers: A and C**

- **A** ✅ — `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`. `@ControllerAdvice` applies globally to all controllers. `@ResponseBody` means the handler methods write to the response body rather than returning view names.
- **C** ✅ — Controller-local `@ExceptionHandler` methods take precedence over global `@RestControllerAdvice` handlers for the same exception type in that controller. Spring searches the local controller first.
- **B** — Wrong. `@Controller` + `@ResponseBody` is the composition of `@RestController`, not `@RestControllerAdvice`. These are different annotations with different purposes.
- **D** — Wrong. `@RestControllerAdvice` applies to all controllers — both `@RestController` and `@Controller` classes. The `@ControllerAdvice` component inside it is not limited to REST controllers.
- **E** — Wrong. `@ExceptionHandler` methods inside `@RestControllerAdvice` can return any type — a plain object, `ResponseEntity`, `String`, or `void`. The `@ResponseBody` from `@RestControllerAdvice` means whatever they return is serialised to the response body. `ResponseEntity` is not required.

---

**Question 4 — Answer: C**

Due to Java's type erasure, generic type information is not available at runtime. `List<Order>.class` is not valid Java syntax — you can't use a parameterised type as a class literal. `getForObject(url, List.class)` compiles but gives you a raw `List` — Jackson deserialises JSON array elements as `LinkedHashMap` rather than `Order`. `ParameterizedTypeReference` uses an anonymous subclass trick to capture the generic type information at compile time and make it available at runtime, allowing Jackson to correctly deserialise to `List<Order>`.

- **A** — Wrong. Returns `List` not `List<Order>`. Elements will be `LinkedHashMap` instances, not `Order` objects. Compiles but produces wrong results.
- **B** — Wrong. `List<Order>.class` is not valid Java syntax. This doesn't compile.
- **D** — Wrong. Same problem as A — `getForEntity(url, List.class)` gives `ResponseEntity<List>` not `ResponseEntity<List<Order>>`.

---

**Question 5 — Answers: A, C, and D**

- **A** ✅ — `<packaging>war</packaging>` tells Maven to produce a WAR artifact instead of a JAR. Required for WAR deployment.
- **C** ✅ — Extending `SpringBootServletInitializer` and overriding `configure()` is the bridge between the external servlet container's initialisation mechanism and Spring Boot. Without it, the external container doesn't know how to start the application.
- **D** ✅ — Marking embedded Tomcat as `provided` excludes it from the WAR. The external container provides its own servlet container — bundling Tomcat inside would cause conflicts.
- **B** — Wrong. The `spring-boot-maven-plugin` is still needed for WAR packaging — it repackages the WAR to make it executable. Removing it would break the build.
- **E** — Wrong. Keep `main()` — it's used when running locally as an executable JAR during development. External containers ignore it but it causes no harm and is actively useful.
- **F** — Wrong. `@EnableWebMvc` is not required for WAR deployment. Adding it would actually disable Spring Boot's MVC auto-configuration — it's an anti-pattern in Boot applications unless you need very fine-grained MVC control.

---

Ready to start **Section 4 — Testing**, beginning with **4.1.1** (JUnit 5 + Mockito) when you give the OK.