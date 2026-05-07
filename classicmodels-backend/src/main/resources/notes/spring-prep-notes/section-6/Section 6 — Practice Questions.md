---

## Section 6 — Practice Questions

---

**Question 1** (Single answer)

A Spring Boot application has the following in `application.properties`:

```properties
server.port=8080
```

The application is started with:

```bash
java -jar order-service.jar --server.port=9090
```

An OS environment variable `SERVER_PORT=7070` is also set. What port does the application start on?

A) 8080 — `application.properties` is the authoritative source  
B) 7070 — OS environment variables override properties files  
C) 9090 — command-line arguments have the highest precedence  
D) Spring throws `ConflictingPropertySourcesException` because multiple sources define the same property

---

**Question 2** (Single answer)

A developer adds `spring-boot-starter-actuator` to a Spring Boot application and starts it with default configuration. Which of the following HTTP requests will return a successful response?

A) `GET /actuator/beans`  
B) `GET /actuator/env`  
C) `GET /actuator/health`  
D) `GET /actuator/metrics`

---

**Question 3** (Multiple answer — Choose 2)

Which two statements correctly describe `@ConditionalOnMissingBean`?

A) It applies the configuration only if no bean of the specified type exists in the context  
B) It applies the configuration only if the specified class is missing from the classpath  
C) Declaring your own bean of the same type causes the autoconfigured bean to be skipped  
D) It applies the configuration only if the specified property is missing from `application.properties`  
E) It is equivalent to `@ConditionalOnMissingClass`

---

**Question 4** (Single answer)

A developer needs to track the current number of active database connections in their application. Which Micrometer meter type is most appropriate?

A) `Counter`  
B) `Timer`  
C) `Gauge`  
D) `DistributionSummary`

---

**Question 5** (Multiple answer — Choose 3)

Which three of the following correctly describe the relationship between `spring-boot-starter-parent` and `spring-boot-dependencies`?

A) `spring-boot-starter-parent` inherits from `spring-boot-dependencies`  
B) `spring-boot-dependencies` is a BOM that contains managed dependency versions  
C) `spring-boot-starter-parent` and `spring-boot-dependencies` are the same artifact  
D) Projects that cannot use `spring-boot-starter-parent` can import `spring-boot-dependencies` directly as a BOM  
E) `spring-boot-starter-parent` provides default plugin configuration in addition to dependency management  
F) `spring-boot-dependencies` includes the `spring-boot-maven-plugin` configuration

---

Take your time and answer all five. I'll explain every option when you're ready.