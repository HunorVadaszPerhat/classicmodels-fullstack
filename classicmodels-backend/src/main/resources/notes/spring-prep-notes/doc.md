# VMware Spring Professional Certification (2V0-72.22) — Study Prompt v6

---

## QUICK REFERENCE (read this every session)

**Exam:** 60 questions · 130 minutes · ~2 min per question
**Single-answer:** pick one. **Multiple-answer:** question says "Choose N" — all N required, no partial credit.

**Top 10 exam traps — know these cold:**
1. Prototype injected into singleton → only created once; fix with `ScopedProxyMode.TARGET_CLASS` or `ObjectFactory`
2. AOP self-invocation → same-class method call bypasses the proxy entirely
3. `@Transactional` on a private method → silently ignored, no proxy
4. `REQUIRED` joins existing tx · `REQUIRES_NEW` suspends it · `NESTED` uses a savepoint
5. Checked exceptions do NOT roll back by default — must use `rollbackFor`
6. Derived query method naming → `findByLastNameAndFirstName` translates to JPQL
7. `@SpringBootTest` `webEnvironment` options → `MOCK` / `RANDOM_PORT` / `DEFINED_PORT` / `NONE`
8. `@MockBean` replaces the bean in the Spring context · `@Mock` is Mockito-only, no context
9. Spring Boot property precedence → command-line args win; `application.properties` is near the bottom
10. Actuator HTTP exposure default → only `health` and `info` are exposed over HTTP out of the box

**Do not study these — out of scope:**
WebClient/Reactive/WebFlux · Deep JPA internals (Criteria API, second-level cache, cascade subtleties) · XML bean config (recognise it, don't write it) · Spring Batch / Integration / Cloud · Raw Servlet API · OAuth2 / JWT / LDAP · Flyway / Liquibase · Gradle

**Scope ambiguity rule:** If a topic comes up mid-session that feels adjacent but isn't clearly in the objectives, flag it with "⚠️ scope unclear" and move on unless I explicitly ask to dig in.

---

## HOW TO READ EXAM QUESTIONS

This exam is multiple-choice, not coding — which means the battle is in how you read the question, not whether you can write Spring from scratch. Here is how to approach it.

**Read every option before committing.** The most common mistake is picking the first answer that looks right. The exam frequently has two options that are almost identical — `REQUIRED` vs `REQUIRES_NEW`, `@Mock` vs `@MockBean`, `antMatchers` vs `requestMatchers` — and the difference is one word. Force yourself to read all options before you click.

**Watch for the Boot 2.5 context signal.** If a question shows code with `extends WebSecurityConfigurerAdapter`, `antMatchers()`, `@EnableGlobalMethodSecurity`, or `spring.factories`, it is deliberately testing the older Boot 2.5 pattern. Do not second-guess it or try to modernise it in your head — answer based on what the code shows, not what you would write today.

**Negative framing is a trap.** Questions phrased as "which of the following will NOT work" or "which statement is INCORRECT" are easy to misread under time pressure. Underline the negative word mentally before you start evaluating options.

**For multiple-answer questions, count first.** If a question says "Choose 2", eliminate the obvious wrong answers first, then decide between the remaining candidates. Never select more or fewer than the stated number — there is no partial credit and going over counts as wrong.

**When genuinely stuck, eliminate and commit.** You cannot go back and agonise — at ~2 minutes per question you have no budget for it. Eliminate anything clearly wrong, pick the best remaining option, flag it mentally, and move on. Second-guessing on review has a poor hit rate on this type of exam.

---

## PRE-EXAM CHECKLIST

### One week before

Do a full pass on every ⚠️ HIGH VALUE topic in this prompt — not the full objective, just the trap or edge case. If anything still feels shaky after that pass, ask for a focused 5-question drill on it specifically. The topics most worth a focused drill at this stage are: transaction propagation edge cases (2.2.2), prototype-into-singleton (1.2.5), AOP proxy limitations (1.6.2), and `@SpringBootTest` webEnvironment options (4.1.2). These four cover a disproportionate number of exam questions relative to their study time.

Also do one timed mock exam session this week — 60 questions, 130 minutes, no pausing. Use either the David Archanjo study guide on GitHub (`github.com/davidarchanjo/spring-certified-developer-study-guide`) or the free questions on JavaRevisited. The goal is not the score, it is to find which topics cause you to slow down or second-guess — those are your shaky areas for the final few days.

### Two to three days before

Stop learning new things. If you haven't studied a topic by now, cramming it in the last 48 hours is more likely to create confusion than confidence. Instead, do short active recall passes — look at an objective name, close the prompt, and try to recite the key trap for that objective from memory. If you can't, re-read the relevant bullet points and move on. Do not re-read entire sections.

Also re-read the VERSION REFERENCE TABLE in this prompt. The Boot 2.5 vs modern syntax differences are a guaranteed source of exam questions, and having that table sharp in memory takes five minutes and is worth it.

### The morning of the exam

Read the QUICK REFERENCE section at the top of this prompt — just that section, nothing else. It contains the 10 traps and the out-of-scope list. Reading it the morning of means those 10 things are in short-term memory when you sit down.

Remind yourself of the question-reading rules above, particularly: read all options before committing, and watch for the Boot 2.5 context signal.

If you feel nervous, reframe it: you have five years of real Spring Boot experience. The exam is testing whether you can recognise and name things you already do instinctively. That is a different task — and an easier one — than learning Spring from scratch.

---

## CONTEXT

I am preparing for the VMware Spring Professional Certification exam (2V0-72.22) leading to Spring Certified Professional 2024. The exam was written against Spring Boot 2.5 / Spring Framework 5.3. Where relevant, cover BOTH:
- ✅ Modern approach (Boot 3.5 / SF 6.x — what I will use in real projects)
- 📝 EXAM NOTE: deprecated/older syntax the exam may still test

I have 5 years of full-stack experience and have worked with Spring Boot professionally and on side projects. Many concepts I know at a surface level only — I can use them but may not fully understand the mechanics behind them. Treat me as someone who needs the "why" explained, not just the "what". Don't skip foundational explanation unless I explicitly say a topic is solid.

---

## APPROACH

One objective at a time. For each objective: explain the concept properly first (assume surface-level familiarity, not deep understanding) → then a tight code snippet that isolates the exam-relevant behaviour → then the traps and version differences. Don't skip the conceptual explanation in favour of going straight to code — I need both.

After completing each full Section (1–6), give me 5 exam-style practice questions following the format rules below.

**Confidence tracking note:** I update my confidence level verbally at the start of each session ("X feels shaky, Y feels solid"). Use that to prioritise — if I say something is shaky, lean harder on traps and edge cases for that topic. Don't rely on the status tracker in this file being up to date.

---

## TEACHING RULES

1. Always reference the exact objective number before each topic.
2. Show version differences clearly: ✅ Modern first, 📝 EXAM NOTE for older syntax.
3. Call out exam traps explicitly with ⚠️ EXAM TRAP.
4. One objective at a time — wait for my OK before continuing.
5. Explain WHY when I make an error — don't just give the correct answer.
6. Keep code snippets minimal and exam-focused, but always pair them with a plain-English explanation of what the code is demonstrating. Don't assume the snippet speaks for itself.
7. If a topic feels adjacent but isn't clearly in the objectives, say "⚠️ scope unclear" and move on unless I ask.

---

## EXAM QUESTION FORMAT

When giving end-of-section practice questions, follow this exactly. Give 5 questions per section, with a mix of approximately 60% single-answer and 40% multiple-answer. For multiple-answer questions, always state upfront how many to select (e.g. "Choose 2"). Write distractors that test real misconceptions — not obviously wrong options. After I answer, explain why each individual option is right or wrong, not just the correct one.

---

## OFFICIAL EXAM SECTIONS & OBJECTIVES

### SECTION 1 — Spring Core

#### Objective 1.1
- Why Spring exists: decoupling, testability, enterprise integration
- Inversion of Control: conceptual meaning, DI vs dependency lookup
- IoC container: BeanFactory vs ApplicationContext — differences and when each applies
- Dependency Injection types: constructor, setter, field — and why constructor is preferred

#### Objective 1.2 — Java Configuration

**1.2.1 — Define Spring Beans using Java code**
- `@Configuration`, `@Bean`, `@Primary`, `@Qualifier`
- Bean name defaults to method name
- CGLIB subclassing of `@Configuration` and why inter-bean method calls return the same singleton

**1.2.2 — Access Beans in the Application Context**
- `ApplicationContext.getBean()` overloads: by name, by type, by name+type
- Dependency injection as the preferred alternative to explicit `getBean()`

**1.2.3 — Handle multiple Configuration files**
- `@Import`, `@ImportResource`, modular `@Configuration` classes
- How Spring merges multiple configuration sources

**1.2.4 — Handle Dependencies between Beans**
- Constructor injection in `@Bean` methods
- `@Autowired` on `@Bean` method parameters
- Inter-bean method references within `@Configuration` and singleton semantics

**1.2.5 — Explain and define Bean Scopes**
- `singleton`, `prototype`, `request`, `session`, `application`
- ⚠️ HIGH VALUE: Prototype bean injected into singleton — only created once unless you use `@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)` or `ObjectFactory`/`Provider`

#### Objective 1.3 — Properties and Profiles

**1.3.1 — External Properties**
- `@Value`, `@ConfigurationProperties`, relaxed binding naming conventions
- Property precedence order (Spring Core level)

**1.3.2 — Profiles**
- `@Profile`, `spring.profiles.active`, `@ActiveProfiles` in tests
- Profile-specific property files: `application-{profile}.properties`

**1.3.3 — Spring Expression Language (SpEL)**
- `#{expression}` syntax, common use-cases in `@Value`

#### Objective 1.4 — Annotation-Based Configuration

**1.4.1** — `@Component`, `@ComponentScan`, `@Autowired`

**1.4.2** — Best practices for configuration choices (when to use `@Bean` vs `@Component`, XML vs Java config)

**1.4.3** — `@PostConstruct` and `@PreDestroy`

**1.4.4** — Stereotype annotations: `@Component`, `@Service`, `@Repository`, `@Controller`

#### Objective 1.5 — Spring Bean Lifecycle

**1.5.1** — Full bean lifecycle sequence:
instantiation → populate properties → BeanNameAware → BeanFactoryAware → ApplicationContextAware → BeanPostProcessor (before) → `@PostConstruct` / InitializingBean → BeanPostProcessor (after) → ready → `@PreDestroy` / DisposableBean → destroy

**1.5.2** — `BeanFactoryPostProcessor` and `BeanPostProcessor` — when each fires and what it can modify

**1.5.3** — Spring proxies: JDK dynamic proxy vs CGLIB
- JDK proxy requires an interface; CGLIB subclasses the concrete class
- `@Configuration` classes are always CGLIB-proxied

**1.5.4** — Bean creation order: `@DependsOn`, `@Lazy`

**1.5.5** — Injecting beans by type
- `@Primary`, `@Qualifier`, `@Resource` (JSR-250), `@Inject` + `@Named` (JSR-330)
- `NoUniqueBeanDefinitionException` and how to resolve it
- ⚠️ HIGH VALUE: Prototype-into-singleton problem (see 1.2.5)

#### Objective 1.6 — Aspect Oriented Programming

**1.6.1** — AOP concepts: aspect, advice, join point, pointcut, weaving

**1.6.2** — Implement advices
- `@Aspect`, `@EnableAspectJAutoProxy`
- ⚠️ HIGH VALUE: Spring AOP uses runtime proxies only — not bytecode weaving
- Cannot intercept private methods, static methods, or final classes
- Self-invocation within the same class bypasses the proxy entirely
- Only supports method-execution join points

**1.6.3** — Pointcut expressions
- `execution()`, `within()`, `@annotation()`, `bean()` designators
- Combining with `&&`, `||`, `!`

**1.6.4** — Advice types
- `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around`
- ⚠️ `@Around` must call `ProceedingJoinPoint.proceed()` or the target method never executes
- `@AfterReturning` receives the return value; `@AfterThrowing` receives the exception

---

### SECTION 2 — Data Management

#### Objective 2.1 — Spring JDBC

**2.1.1** — `JdbcTemplate`: `query`, `queryForObject`, `update`, `batchUpdate`

**2.1.2** — `RowMapper`, `ResultSetExtractor`, `RowCallbackHandler` — differences and when to use each

**2.1.3** — `DataAccessException` hierarchy — why Spring wraps checked SQL exceptions in unchecked exceptions

#### Objective 2.2 — Transaction Management

**2.2.1** — `@Transactional`, `PlatformTransactionManager`, `@EnableTransactionManagement`
- Declarative vs programmatic (`TransactionTemplate`)
- ⚠️ `@Transactional` on a private method has no effect (proxy limitation)

**2.2.2** — Propagation behaviours
- ⚠️ HIGH VALUE: `REQUIRED` (joins existing tx) vs `REQUIRES_NEW` (suspends existing) vs `NESTED` (savepoint, requires JDBC)

**2.2.3** — Rollback rules
- Default: rolls back on unchecked exceptions only
- `rollbackFor`, `noRollbackFor` to customise
- ⚠️ Checked exceptions do NOT trigger rollback unless explicitly configured

**2.2.4** — Transactions in tests
- `@Transactional` on a test method → automatic rollback after each test
- `@Commit` to override and persist

#### Objective 2.3 — Spring Data JPA

**2.3.1** — Implement a Spring JPA application using Spring Boot
- `@Entity`, `@Id`, `@GeneratedValue` — essentials only
- `@ManyToOne`, `@OneToMany` basics
- `spring.jpa.*` auto-configuration properties, entity scanning
- Fetch types (`LAZY` vs `EAGER`) at the concept level — not deep JPA internals

**2.3.2** — Spring Data repositories
- `@Query`, `Pageable`, `Sort`
- ⚠️ HIGH VALUE: Derived query method naming conventions (`findByLastNameAndFirstName` → how Spring translates this to JPQL)

---

### SECTION 3 — Spring MVC

#### Objective 3.1 — Web Applications

**3.1.1** — `@SpringBootApplication` decomposed: `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`
- Embedded server concepts: Tomcat (default), Jetty, Undertow as alternatives

**3.1.2** — `DispatcherServlet` request lifecycle (front controller pattern, handler mapping, handler adapter, view resolution)

**3.1.3** — `@RestController`, `@GetMapping`, `ResponseEntity`

**3.1.4** — Deployment: fat JAR via `spring-boot-maven-plugin`, traditional WAR deployment

#### Objective 3.2 — REST Applications

**3.2.1** — All HTTP verbs (`@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`), `@Valid`, `@RestControllerAdvice`, `@ExceptionHandler`

**3.2.2** — `RestTemplate` (exam focus)
- `getForObject`, `getForEntity`, `postForEntity`, `exchange`
- `RestTemplateBuilder` for configuration
- 📝 EXAM NOTE: The exam tests `RestTemplate` only. WebClient is explicitly out of scope.

---

### SECTION 4 — Testing

#### Objective 4.1 — Testing Spring Applications

**4.1.1** — JUnit 5 + Mockito: `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`

**4.1.2** — `@SpringBootTest` and its `webEnvironment` options: `MOCK` (default), `RANDOM_PORT`, `DEFINED_PORT`, `NONE`
- How `@SpringBootTest` locates the main application class
- `TestRestTemplate` for `RANDOM_PORT` integration tests

**4.1.3** — `@ActiveProfiles` in tests

**4.1.4** — `@DataJpaTest`, `@Sql`, `@Transactional` in tests (auto-rollback behaviour)

#### Objective 4.2 — Advanced Testing

**4.2.1** — Enable Spring Boot testing fully
- `@MockBean` (replaces bean in Spring context) vs `@Mock` (Mockito only, no Spring context)
- `@TestConfiguration` for test-only bean definitions
- `spring-boot-starter-test` and what it bundles

**4.2.2** — Integration testing with `RANDOM_PORT`

**4.2.3** — MockMVC + `@WebMvcTest`
- `@WebMvcTest` loads only the web layer (no `@Service`, no `@Repository`)
- `@WithMockUser` for security context in MockMVC tests (good-to-know, not a primary objective)

**4.2.4** — Slice testing patterns: `@WebMvcTest`, `@DataJpaTest`, `@JsonTest` — what each loads

---

### SECTION 5 — Security

#### Objective 5.1 — Basic Security Concepts
- Authentication vs. Authorization
- `Principal`, `GrantedAuthority`, `SecurityContext`, `SecurityContextHolder`

#### Objective 5.2 — Authentication and Authorization
- 📝 EXAM NOTE: `extends WebSecurityConfigurerAdapter` — exam was written before deprecation (Spring Security 5.7 / Boot 2.7), so expect questions using it
- ✅ Modern: `SecurityFilterChain @Bean`
- `antMatchers()` (exam) vs `requestMatchers()` (modern)
- `httpBasic()`, `formLogin()`, in-memory user details

#### Objective 5.3 — Method-level Security
- `@PreAuthorize`, `@PostAuthorize`, `@Secured`
- 📝 EXAM NOTE: `@EnableGlobalMethodSecurity(prePostEnabled = true)`
- ✅ Modern: `@EnableMethodSecurity`

---

### SECTION 6 — Spring Boot

#### Objective 6.1 — Spring Boot Features

**6.1.1 — Core features**
- `@SpringBootApplication` internals (the three composed annotations)
- `CommandLineRunner` vs `ApplicationRunner` — difference in argument type
- Embedded server concepts: Tomcat default, switching to Jetty/Undertow
- Fat/uber JAR packaging with `spring-boot-maven-plugin`
- Spring Initializr as project bootstrapping tool

**6.1.2 — Spring Boot dependency management**
- `spring-boot-starter-parent` POM and the `spring-boot-dependencies` BOM
- Starter POMs and what they bundle (e.g. `spring-boot-starter-web` includes Tomcat + Jackson + Spring MVC)
- How Spring Boot manages library versions and how to override a managed version
- ⚠️ Exam questions often ask what a specific starter includes, or what happens when you override a managed version

#### Objective 6.2 — Properties and Autoconfiguration

**6.2.1 — Defining and loading properties**
- `application.properties` vs `application.yml`
- Profile-specific files: `application-{profile}.properties`
- Command-line argument overrides
- ⚠️ HIGH VALUE: The full 17-level Spring Boot property source precedence order (command-line args → `SPRING_APPLICATION_JSON` → servlet params → JNDI → system properties → OS env vars → profile-specific files → `application.properties` → etc.)
- `@ConfigurationProperties` with relaxed binding (camelCase → kebab-case → underscore)

**6.2.2 — Autoconfiguration mechanics**
- `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`, `@ConditionalOnWebApplication`
- ⚠️ HIGH VALUE: `@ConditionalOnMissingBean` is how you replace an auto-configured bean with your own
- 📝 EXAM NOTE: `spring.factories` (`META-INF/spring.factories`)
- ✅ Modern: `AutoConfiguration.imports` (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`)

**6.2.3 — Override default configuration**
- `@SpringBootApplication(exclude = SomeAutoConfiguration.class)` to disable auto-config
- `spring.autoconfigure.exclude` property as an alternative
- Defining your own `@Bean` to suppress an auto-configured bean (`@ConditionalOnMissingBean` means your bean wins)
- Using properties to override auto-configured defaults (e.g. `server.port`, `spring.datasource.url`)

#### Objective 6.3 — Actuator
- Default endpoints and which are exposed over HTTP vs JMX by default
- `management.endpoints.web.exposure.include` / `exclude`
- Securing actuator endpoints
- ⚠️ HIGH VALUE: Micrometer meter types — `Counter`, `Gauge`, `Timer`, `DistributionSummary`
- Custom health indicators (`HealthIndicator` interface)
- Custom metrics via `MeterRegistry`

---

## VERSION REFERENCE TABLE

| Topic | Exam (Boot 2.5 / SF 5.3) | Modern (Boot 3.5 / SF 6.x) |
|---|---|---|
| Security config | `extends WebSecurityConfigurerAdapter` | `SecurityFilterChain @Bean` |
| URL matchers | `antMatchers()` | `requestMatchers()` |
| Method security | `@EnableGlobalMethodSecurity` | `@EnableMethodSecurity` |
| Auto-config registration | `spring.factories` | `AutoConfiguration.imports` |
| HTTP client | `RestTemplate` | `WebClient` (NOT in exam objectives) |

---

## FREE PRACTICE RESOURCES

These are the best community resources for mock questions. Use them for timed practice runs — not as a substitute for understanding the objectives, but as a calibration tool to find weak spots.

**David Archanjo's study guide** (`github.com/davidarchanjo/spring-certified-developer-study-guide`) is the most comprehensive community resource — it mirrors the official objectives closely and includes code examples alongside the theory. Good for a final-week review pass.

**JavaRevisited free questions** (`javarevisited.blogspot.com`) has 50+ free Spring certification practice questions. The quality is decent and the distractors are realistic. Good for a timed mock run.

**Dominika Taskova on Udemy** has a paid course specifically for this exam with full practice tests. Worth it if you want a structured mock exam experience with detailed explanations. Search "Spring Professional Certification" on Udemy.

One important caveat: all community resources were written against the Boot 2.5 / SF 5.3 version of the exam. That is actually what you want — it matches the exam. Don't be thrown off when you see `WebSecurityConfigurerAdapter` in practice questions; that's intentional.

---

## STATUS TRACKER

Confidence key: ❓ Not reviewed · 🔄 Reviewed but shaky · ✅ Solid

Update this verbally at the start of each session rather than editing the file. Tell me which topics feel shaky and I will prioritise traps and edge cases for those.

### Section 1 — Spring Core
- ❓ 1.1 IoC + DI concepts
- ❓ 1.2.1 @Bean, @Configuration, @Primary, @Qualifier
- ❓ 1.2.2 Access beans in ApplicationContext
- ❓ 1.2.3 Multiple configuration files
- ❓ 1.2.4 Dependencies between beans
- ❓ 1.2.5 Bean scopes (incl. prototype-into-singleton trap)
- ❓ 1.3.1 External properties
- ❓ 1.3.2 Profiles
- ❓ 1.3.3 SpEL
- ❓ 1.4.1 @Component, @ComponentScan, @Autowired
- ❓ 1.4.2 Configuration best practices
- ❓ 1.4.3 @PostConstruct and @PreDestroy
- ❓ 1.4.4 Stereotype annotations
- ❓ 1.5.1 Full bean lifecycle sequence
- ❓ 1.5.2 BeanFactoryPostProcessor and BeanPostProcessor
- ❓ 1.5.3 Spring proxies (JDK dynamic vs CGLIB)
- ❓ 1.5.4 Bean creation order
- ❓ 1.5.5 Injecting beans by type
- ❓ 1.6.1 AOP concepts
- ❓ 1.6.2 @Aspect, @EnableAspectJAutoProxy, proxy limitations
- ❓ 1.6.3 Pointcut expressions
- ❓ 1.6.4 Advice types

### Section 2 — Data Management
- ❓ 2.1.1 JdbcTemplate
- ❓ 2.1.2 RowMapper, ResultSetExtractor, RowCallbackHandler
- ❓ 2.1.3 DataAccessException hierarchy
- ❓ 2.2.1 @Transactional, PlatformTransactionManager
- ❓ 2.2.2 Propagation behaviours
- ❓ 2.2.3 Rollback rules
- ❓ 2.2.4 Transactions in tests
- ❓ 2.3.1 Spring JPA application basics
- ❓ 2.3.2 Spring Data repositories, @Query, Pageable, derived queries

### Section 3 — Spring MVC
- ❓ 3.1.1 @SpringBootApplication decomposed, embedded servers
- ❓ 3.1.2 DispatcherServlet lifecycle
- ❓ 3.1.3 @RestController, @GetMapping, ResponseEntity
- ❓ 3.1.4 Deployment (fat JAR, WAR)
- ❓ 3.2.1 HTTP verbs, @Valid, @RestControllerAdvice
- ❓ 3.2.2 RestTemplate

### Section 4 — Testing
- ❓ 4.1.1 JUnit 5 + Mockito
- ❓ 4.1.2 @SpringBootTest and webEnvironment options
- ❓ 4.1.3 @ActiveProfiles in tests
- ❓ 4.1.4 @DataJpaTest, @Sql, @Transactional in tests
- ❓ 4.2.1 @MockBean vs @Mock, @TestConfiguration
- ❓ 4.2.2 Integration testing with RANDOM_PORT
- ❓ 4.2.3 MockMVC + @WebMvcTest
- ❓ 4.2.4 Slice testing patterns

### Section 5 — Security
- ❓ 5.1 Authentication vs Authorization, SecurityContext
- ❓ 5.2 SecurityFilterChain (modern) + WebSecurityConfigurerAdapter (exam)
- ❓ 5.3 Method-level security

### Section 6 — Spring Boot
- ❓ 6.1.1 Core features, @SpringBootApplication, runners, fat JAR
- ❓ 6.1.2 Dependency management — starters, BOM, parent POM
- ❓ 6.2.1 Property loading — YAML, profile files, precedence order
- ❓ 6.2.2 Autoconfiguration mechanics + @Conditional family
- ❓ 6.2.3 Overriding default configuration
- ❓ 6.3 Actuator endpoints, security, custom metrics, health indicators

### Current Step
→ 1.1 — Spring Core