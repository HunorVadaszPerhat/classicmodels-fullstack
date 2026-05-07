# The Exhaustive Spring Framework 7 & Spring Boot 4 Annotation Reference

> Targets **Spring Framework 7.x** (Jakarta EE 11, JDK 17 baseline / JDK 25 recommended) and **Spring Boot 4.x** (Jackson 3, modular auto-configure, JSpecify null-safety, first-class API versioning, built-in resilience).
>
> Annotations are shown with their **fully qualified package** so you can disambiguate collisions (e.g. `jakarta.*` vs `org.springframework.*` vs `org.jspecify.*`).

---

## Table of Contents

1. [Bootstrapping & Application](#1-bootstrapping--application)
2. [Stereotypes & Component Scanning](#2-stereotypes--component-scanning)
3. [Java Configuration & Imports](#3-java-configuration--imports)
4. [Dependency Injection & Wiring](#4-dependency-injection--wiring)
5. [Bean Lifecycle, Scope & Ordering](#5-bean-lifecycle-scope--ordering)
6. [Externalized Configuration & Profiles](#6-externalized-configuration--profiles)
7. [Conditional Annotations](#7-conditional-annotations)
8. [Auto-Configuration (Spring Boot)](#8-auto-configuration-spring-boot)
9. [Web MVC — Controllers & Request Mapping](#9-web-mvc--controllers--request-mapping)
10. [Web MVC — Arguments, Binding & Advice](#10-web-mvc--arguments-binding--advice)
11. [API Versioning (Spring 7 first-class)](#11-api-versioning-spring-7-first-class)
12. [WebFlux Reactive](#12-webflux-reactive)
13. [HTTP Interface Clients (Declarative)](#13-http-interface-clients-declarative)
14. [WebSocket, STOMP & RSocket](#14-websocket-stomp--rsocket)
15. [Messaging — JMS, Kafka, AMQP, Pulsar](#15-messaging--jms-kafka-amqp-pulsar)
16. [Transactions](#16-transactions)
17. [Spring Data — Core, JPA, Mongo, R2DBC](#17-spring-data--core-jpa-mongo-r2dbc)
18. [JPA / Jakarta Persistence](#18-jpa--jakarta-persistence)
19. [Bean Validation (Jakarta Validation 3.1)](#19-bean-validation-jakarta-validation-31)
20. [AOP / AspectJ](#20-aop--aspectj)
21. [Events](#21-events)
22. [Scheduling & Async](#22-scheduling--async)
23. [Caching](#23-caching)
24. [Resilience (Spring 7 core)](#24-resilience-spring-7-core)
25. [Null-Safety — JSpecify](#25-null-safety--jspecify)
26. [AOT / Native Image Hints](#26-aot--native-image-hints)
27. [Observability — Micrometer](#27-observability--micrometer)
28. [Actuator Endpoints](#28-actuator-endpoints)
29. [Spring Security 7](#29-spring-security-7)
30. [Testing](#30-testing)
31. [Spring Cloud (Portfolio Highlights)](#31-spring-cloud-portfolio-highlights)
32. [Spring Batch & Integration](#32-spring-batch--integration)
33. [Lesser-Known / Meta Annotations](#33-lesser-known--meta-annotations)

---

## 1. Bootstrapping & Application

| Annotation | Package | Purpose |
|---|---|---|
| `@SpringBootApplication` | `org.springframework.boot.autoconfigure` | Meta-annotation = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`. Main class. |
| `@SpringBootConfiguration` | `org.springframework.boot` | `@Configuration` variant that Spring Boot test slices detect as the primary config. |
| `@EnableAutoConfiguration` | `org.springframework.boot.autoconfigure` | Triggers classpath-driven auto-configuration via `AutoConfigurationImportSelector` and `AutoConfigurationPackage`. Supports `exclude` / `excludeName`. |
| `@AutoConfigurationPackage` | `org.springframework.boot.autoconfigure` | Registers the package of the annotated class as a "default" base for `@EntityScan`, `@EnableJpaRepositories`, etc. |
| `@EnableConfigurationProperties` | `org.springframework.boot.context.properties` | Explicitly registers `@ConfigurationProperties` classes as beans. |
| `@ConfigurationPropertiesScan` | `org.springframework.boot.context.properties` | Auto-scans packages for `@ConfigurationProperties` classes. |
| `@ServletComponentScan` | `org.springframework.boot.web.servlet` | Scans for `@WebServlet`, `@WebFilter`, `@WebListener` when running embedded. |

---

## 2. Stereotypes & Component Scanning

| Annotation | Package | Purpose |
|---|---|---|
| `@Component` | `org.springframework.stereotype` | Generic Spring-managed bean. Root stereotype. |
| `@Service` | `org.springframework.stereotype` | Business service layer marker. |
| `@Repository` | `org.springframework.stereotype` | Persistence layer; enables `PersistenceExceptionTranslationPostProcessor` translation. |
| `@Controller` | `org.springframework.stereotype` | Spring MVC controller. |
| `@RestController` | `org.springframework.web.bind.annotation` | `@Controller` + `@ResponseBody`. |
| `@ControllerAdvice` | `org.springframework.web.bind.annotation` | Global controller advice (exception handlers, `@ModelAttribute`, `@InitBinder`). |
| `@RestControllerAdvice` | `org.springframework.web.bind.annotation` | `@ControllerAdvice` + `@ResponseBody`. |
| `@Configuration` | `org.springframework.context.annotation` | `@Component` specialization; class is a Java config source. `proxyBeanMethods` controls CGLIB enhancement. |
| `@ComponentScan` | `org.springframework.context.annotation` | Controls package scanning: `basePackages`, `basePackageClasses`, `includeFilters`, `excludeFilters`, `lazyInit`, `nameGenerator`, `scopedProxy`. |
| `@ComponentScans` | `org.springframework.context.annotation` | Container for multiple `@ComponentScan`. |
| `@Indexed` | `org.springframework.stereotype` | Marks classes for inclusion in `spring.components` build-time index (faster startup). |
| `@ManagedBean` | `jakarta.annotation` | JSR-250 equivalent of `@Component`. |
| `@Named` | `jakarta.inject` | JSR-330 equivalent of `@Component("name")`. |

---

## 3. Java Configuration & Imports

| Annotation | Package | Purpose |
|---|---|---|
| `@Bean` | `org.springframework.context.annotation` | Method-level bean registration. Attributes: `name`, `initMethod`, `destroyMethod`, `autowireCandidate`, `defaultCandidate`. |
| `@Import` | `org.springframework.context.annotation` | Imports `@Configuration` classes, `ImportSelector`, `ImportBeanDefinitionRegistrar`, or `BeanRegistrar` (new in Spring 7). |
| `@ImportResource` | `org.springframework.context.annotation` | Imports XML or Groovy bean definition files. |
| `@ImportAutoConfiguration` | `org.springframework.boot.autoconfigure` | Selectively imports auto-configurations (common in test slices). |
| `@ImportHttpServices` | `org.springframework.web.service.registry` | **New in Spring 7.** Declaratively registers `@HttpExchange` interface clients with a group. |
| `@ImportRuntimeHints` | `org.springframework.context.annotation` | Registers `RuntimeHintsRegistrar` for AOT / GraalVM. |
| `@PropertySource` | `org.springframework.context.annotation` | Loads a properties file into the `Environment`. |
| `@PropertySources` | `org.springframework.context.annotation` | Container for multiple `@PropertySource`. |
| `@Description` | `org.springframework.context.annotation` | Human-readable description attached to a bean definition. |
| `@Role` | `org.springframework.context.annotation` | Marks bean as `ROLE_APPLICATION`, `ROLE_SUPPORT`, `ROLE_INFRASTRUCTURE`. |

---

## 4. Dependency Injection & Wiring

| Annotation | Package | Purpose |
|---|---|---|
| `@Autowired` | `org.springframework.beans.factory.annotation` | Type-based injection. Supports `required=false`. Constructor, field, setter, method. |
| `@Inject` | `jakarta.inject` | JSR-330 equivalent of `@Autowired`. |
| `@Resource` | `jakarta.annotation` | JSR-250 name-based (then type-based) injection. |
| `@Qualifier` | `org.springframework.beans.factory.annotation` | Disambiguates by name or custom qualifier. |
| `@Qualifier` (javax/jakarta) | `jakarta.inject` | JSR-330 qualifier. |
| `@Primary` | `org.springframework.context.annotation` | Default among multiple candidates. |
| `@Fallback` | `org.springframework.context.annotation` | **Introduced Spring 6.2.** Bean used only if no non-fallback candidates exist (opposite of `@Primary`). |
| `@Lazy` | `org.springframework.context.annotation` | Lazy init / lazy injection proxy. |
| `@Value` | `org.springframework.beans.factory.annotation` | SpEL / property placeholder injection. |
| `@DependsOn` | `org.springframework.context.annotation` | Force initialization order. |
| `@Lookup` | `org.springframework.beans.factory.annotation` | Method-level injection of prototype beans into singletons. |
| `@PostConstruct` | `jakarta.annotation` | Post-DI initialization callback. |
| `@PreDestroy` | `jakarta.annotation` | Pre-shutdown cleanup callback. |
| `@ConstructorProperties` | `java.beans` | Names constructor params for property binding (still honored by Spring Boot). |

---

## 5. Bean Lifecycle, Scope & Ordering

| Annotation | Package | Purpose |
|---|---|---|
| `@Scope` | `org.springframework.context.annotation` | `singleton`, `prototype`, `request`, `session`, `application`, `websocket`, custom. `proxyMode` = `INTERFACES` / `TARGET_CLASS`. |
| `@RequestScope` | `org.springframework.web.context.annotation` | Shortcut for HTTP-request scope. |
| `@SessionScope` | `org.springframework.web.context.annotation` | HTTP-session scope. |
| `@ApplicationScope` | `org.springframework.web.context.annotation` | `ServletContext`-scope. |
| `@JobScope` | `org.springframework.batch.core.configuration.annotation` | Spring Batch job scope. |
| `@StepScope` | `org.springframework.batch.core.configuration.annotation` | Spring Batch step scope. |
| `@Singleton` | `jakarta.inject` | JSR-330 singleton marker (rarely used alone in Spring). |
| `@Order` | `org.springframework.core.annotation` | Defines ordering for collection injection, advisors, filters, listeners. |
| `@Priority` | `jakarta.annotation` | JSR-250 priority (lower wins). Used with `@Qualifier` for autowiring disambiguation. |

---

## 6. Externalized Configuration & Profiles

| Annotation | Package | Purpose |
|---|---|---|
| `@ConfigurationProperties` | `org.springframework.boot.context.properties` | Binds `application.yml`/`.properties` to a POJO. Attributes: `prefix`, `ignoreUnknownFields`, `ignoreInvalidFields`. |
| `@ConstructorBinding` | `org.springframework.boot.context.properties.bind` | Forces constructor-based binding (default for records and Kotlin data classes). |
| `@DefaultValue` | `org.springframework.boot.context.properties.bind` | Default for a constructor-bound parameter. |
| `@NestedConfigurationProperty` | `org.springframework.boot.context.properties` | Marks a nested type for metadata generation (the annotation processor documents it). |
| `@DeprecatedConfigurationProperty` | `org.springframework.boot.context.properties` | Flags a deprecated property for metadata. |
| `@Name` | `org.springframework.boot.context.properties.bind` | Overrides the bound property name when Java param names aren't usable. |
| `@Profile` | `org.springframework.context.annotation` | Activates a bean/class based on active profile(s). Supports profile expressions (`!prod`, `dev & !mock`). |

---

## 7. Conditional Annotations

Core:

| Annotation | Package | Purpose |
|---|---|---|
| `@Conditional` | `org.springframework.context.annotation` | Custom `Condition` implementations. |

Spring Boot (all in `org.springframework.boot.autoconfigure.condition`):

| Annotation | Purpose |
|---|---|
| `@ConditionalOnBean` | Match if named/type bean exists. |
| `@ConditionalOnMissingBean` | Match if no matching bean — cornerstone of user-overridable auto-config. |
| `@ConditionalOnClass` | Match if class present on classpath. |
| `@ConditionalOnMissingClass` | Match if class absent. |
| `@ConditionalOnProperty` | Match on property value (`havingValue`, `matchIfMissing`). |
| `@ConditionalOnBooleanProperty` | Typed boolean-only variant. |
| `@ConditionalOnExpression` | SpEL expression. |
| `@ConditionalOnResource` | Resource on classpath. |
| `@ConditionalOnWebApplication` | Servlet / Reactive / any web app. |
| `@ConditionalOnNotWebApplication` | Non-web. |
| `@ConditionalOnWarDeployment` | Traditional WAR deployment only. |
| `@ConditionalOnJava` | JDK version range. |
| `@ConditionalOnJndi` | JNDI names resolve. |
| `@ConditionalOnCloudPlatform` | Cloud Foundry, Kubernetes, Heroku, Sap, Azure, etc. |
| `@ConditionalOnSingleCandidate` | Only one candidate of a type exists (or one is primary). |
| `@ConditionalOnThreading` | Platform vs virtual threads. |
| `@ConditionalOnAvailableEndpoint` | Actuator endpoint exposed. |
| `@ConditionalOnEnabledHealthIndicator` | Health indicator enabled. |
| `@ConditionalOnEnabledInfoContributor` | Info contributor enabled. |
| `@ConditionalOnEnabledResourceChain` | MVC resource chain enabled. |
| `@ConditionalOnDefaultWebSecurity` | No user-defined security configuration. |
| `@ConditionalOnDockerCompose` | Docker Compose integration active. |

---

## 8. Auto-Configuration (Spring Boot)

| Annotation | Package | Purpose |
|---|---|---|
| `@AutoConfiguration` | `org.springframework.boot.autoconfigure` | Replaces plain `@Configuration` in auto-config classes; supports `before`, `after`, `beforeName`, `afterName`. |
| `@AutoConfigureBefore` | `org.springframework.boot.autoconfigure` | Order ahead of listed auto-configs. |
| `@AutoConfigureAfter` | `org.springframework.boot.autoconfigure` | Order after listed auto-configs. |
| `@AutoConfigureOrder` | `org.springframework.boot.autoconfigure` | Absolute ordering (rarely needed). |

> **Spring Boot 4 split** the monolithic `spring-boot-autoconfigure` into multiple modules — you may now pull only the auto-config modules you use, but the annotations are unchanged.

---

## 9. Web MVC — Controllers & Request Mapping

| Annotation | Package | Purpose |
|---|---|---|
| `@RequestMapping` | `org.springframework.web.bind.annotation` | Generic mapping. Attributes: `path`, `method`, `params`, `headers`, `consumes`, `produces`, and — **new in Spring 7** — `version`. |
| `@GetMapping` | same | Shortcut for `GET`. |
| `@PostMapping` | same | Shortcut for `POST`. |
| `@PutMapping` | same | Shortcut for `PUT`. |
| `@DeleteMapping` | same | Shortcut for `DELETE`. |
| `@PatchMapping` | same | Shortcut for `PATCH`. |
| `@CrossOrigin` | same | CORS configuration per-controller/method. |
| `@ResponseStatus` | same | Sets HTTP status for handlers / exception classes. |
| `@ResponseBody` | same | Body is serialized (built into `@RestController`). |
| `@SessionAttributes` | same | Model attributes stored in session. |
| `@Mapping` | `org.springframework.web.bind.annotation` | Meta-annotation for composing custom mappings. |

---

## 10. Web MVC — Arguments, Binding & Advice

| Annotation | Package | Purpose |
|---|---|---|
| `@PathVariable` | `org.springframework.web.bind.annotation` | URI template variable. |
| `@RequestParam` | same | Query / form parameter. |
| `@RequestHeader` | same | Single HTTP header. |
| `@RequestBody` | same | Deserialized body. |
| `@RequestPart` | same | Multipart request part. |
| `@CookieValue` | same | Cookie value. |
| `@MatrixVariable` | same | Matrix URI variable. |
| `@ModelAttribute` | same | Model attribute binding / pre-populate. |
| `@SessionAttribute` | same | Pre-existing session attribute (read). |
| `@RequestAttribute` | same | Pre-existing request attribute. |
| `@InitBinder` | same | Customizes `WebDataBinder` registration (formatters, validators). |
| `@ExceptionHandler` | same | Method handles specific exception types. |
| `@Validated` | `org.springframework.validation.annotation` | Triggers method-level bean validation; supports validation groups. |
| `@Valid` | `jakarta.validation` | Triggers nested validation. |

---

## 11. API Versioning (Spring 7 first-class)

Spring Framework 7 introduces a `version` attribute across request-mapping annotations. Works with **path, header, query parameter, or media-type parameter** strategies depending on your `ApiVersionConfigurer`.

```java
@RestController
@RequestMapping("/api/accounts")
class AccountController {

    @GetMapping(path = "/{id}", version = "1")
    AccountV1 getV1(@PathVariable String id) { ... }

    @GetMapping(path = "/{id}", version = "1.1+")   // greater-than-or-equal
    AccountV2 getV11Plus(@PathVariable String id) { ... }
}
```

For functional endpoints: `RequestPredicates.version("1.2")`.

---

## 12. WebFlux Reactive

All Spring MVC annotations above are reused. Additionally:

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableWebFlux` | `org.springframework.web.reactive.config` | Imports reactive-web configuration. |
| `@ResponseBody` / `@RestController` | same as MVC | Applied to reactive handlers. |
| `@ControllerAdvice` / `@RestControllerAdvice` | same | Global reactive advice. |

WebFlux-specific request-predicate equivalents exist in `RouterFunctions` but are not annotation-based.

---

## 13. HTTP Interface Clients (Declarative)

Replaces most uses of OpenFeign. Spring 7 adds the registry layer.

| Annotation | Package | Purpose |
|---|---|---|
| `@HttpExchange` | `org.springframework.web.service.annotation` | Declarative client interface marker; supports `url`, `contentType`, `accept`, `version`. |
| `@GetExchange` | same | HTTP GET shortcut. |
| `@PostExchange` | same | HTTP POST. |
| `@PutExchange` | same | HTTP PUT. |
| `@DeleteExchange` | same | HTTP DELETE. |
| `@PatchExchange` | same | HTTP PATCH. |
| `@ImportHttpServices` | `org.springframework.web.service.registry` | **New in Spring 7.** Registers interfaces into an HTTP service group; the framework creates proxies as beans. |

Works with `RestClient`, `WebClient`, or `RSocketRequester`. Recognizes `@ClientRegistrationId` from Spring Security 7 for OAuth.

---

## 14. WebSocket, STOMP & RSocket

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableWebSocket` | `org.springframework.web.socket.config.annotation` | Raw WebSocket support. |
| `@EnableWebSocketMessageBroker` | same | STOMP message broker. |
| `@MessageMapping` | `org.springframework.messaging.handler.annotation` | STOMP / RSocket destination mapping. |
| `@SubscribeMapping` | `org.springframework.messaging.simp.annotation` | STOMP subscribe. |
| `@SendTo` | `org.springframework.messaging.handler.annotation` | Destination for return value. |
| `@SendToUser` | `org.springframework.messaging.simp.annotation` | User-specific destination. |
| `@DestinationVariable` | `org.springframework.messaging.handler.annotation` | Path variable in destination. |
| `@ConnectMapping` | `org.springframework.messaging.rsocket.annotation` | RSocket setup/connection handler. |
| `@RSocketExchange` | `org.springframework.messaging.rsocket.service` | Declarative RSocket client (analog of `@HttpExchange`). |
| `@Payload` | `org.springframework.messaging.handler.annotation` | Message payload argument. |
| `@Header` / `@Headers` | same | Individual / all message headers. |

---

## 15. Messaging — JMS, Kafka, AMQP, Pulsar

**JMS**

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableJms` | `org.springframework.jms.annotation` | Enables `@JmsListener` support. |
| `@JmsListener` | same | Declarative JMS listener. Repeatable. |
| `@JmsListeners` | same | Container. |

> Spring Framework 7 also adds `JmsClient`, a fluent client, but it is API-level, not annotation-based.

**Kafka**

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableKafka` | `org.springframework.kafka.annotation` | Enables `@KafkaListener`. |
| `@KafkaListener` | same | Declarative Kafka consumer. Repeatable. |
| `@KafkaListeners` | same | Container. |
| `@KafkaHandler` | same | Multi-type listener method dispatch. |
| `@RetryableTopic` | `org.springframework.kafka.annotation` | Non-blocking retry + DLT topology. |
| `@DltHandler` | same | Dead-letter handler method. |
| `@PartitionOffset` | same | Per-partition start-offset config. |
| `@TopicPartition` | same | Explicit topic/partition listener. |

**AMQP / RabbitMQ**

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableRabbit` | `org.springframework.amqp.rabbit.annotation` | Enables `@RabbitListener`. |
| `@RabbitListener` | same | Declarative AMQP listener. |
| `@RabbitHandler` | same | Multi-type dispatch. |
| `@QueueBinding`, `@Queue`, `@Exchange` | same | Declarative topology inside `@RabbitListener(bindings = ...)`. |

**Pulsar** (`spring-pulsar`):

| Annotation | Package | Purpose |
|---|---|---|
| `@EnablePulsar` | `org.springframework.pulsar.annotation` | Enables listener infrastructure. |
| `@PulsarListener` | same | Declarative consumer. |
| `@ReactivePulsarListener` | same | Reactive variant. |
| `@PulsarReader` | same | Reader API. |

---

## 16. Transactions

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableTransactionManagement` | `org.springframework.transaction.annotation` | Enables annotation-driven transactions. |
| `@Transactional` | `org.springframework.transaction.annotation` | Declarative TX. Attributes: `propagation`, `isolation`, `timeout`, `readOnly`, `rollbackFor`, `noRollbackFor`, `transactionManager`, `label`. |
| `@Transactional` | `jakarta.transaction` | Jakarta alternative honored by Spring. |
| `@TransactionalEventListener` | `org.springframework.transaction.event` | Event listener bound to TX phase (`BEFORE_COMMIT`, `AFTER_COMMIT`, `AFTER_ROLLBACK`, `AFTER_COMPLETION`). |

---

## 17. Spring Data — Core, JPA, Mongo, R2DBC

**Commons:**

| Annotation | Package | Purpose |
|---|---|---|
| `@Query` | `org.springframework.data.jpa.repository` (and per-store variants) | Declarative query. |
| `@Modifying` | `org.springframework.data.jpa.repository` | Marks update/delete queries. |
| `@Param` | `org.springframework.data.repository.query` | Named parameter binding. |
| `@NoRepositoryBean` | `org.springframework.data.repository` | Exclude repo interface from instantiation. |
| `@RepositoryDefinition` | `org.springframework.data.repository` | Mark non-extending interfaces as repositories. |
| `@RepositoryEventHandler`, `@HandleBeforeCreate`, `@HandleAfterCreate`, `@HandleBeforeSave`, etc. | `org.springframework.data.rest.core.annotation` | REST exporter event hooks. |
| `@RestResource` | `org.springframework.data.rest.core.annotation` | Customize REST exposure. |

**Auditing:**

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableJpaAuditing` | `org.springframework.data.jpa.repository.config` | Enables JPA auditing. |
| `@EnableMongoAuditing` / `@EnableR2dbcAuditing` / `@EnableReactiveMongoAuditing` | respective packages | Store-specific auditing. |
| `@CreatedDate` | `org.springframework.data.annotation` | Auto-populated creation timestamp. |
| `@CreatedBy` | same | Auto-populated creator. |
| `@LastModifiedDate` | same | Auto-populated update timestamp. |
| `@LastModifiedBy` | same | Auto-populated modifier. |
| `@Version` | `org.springframework.data.annotation` | Store-agnostic optimistic locking (distinct from JPA's `@Version`). |

**Mongo:** `@Document`, `@Field`, `@Indexed`, `@CompoundIndex`, `@TextIndexed`, `@DBRef`, `@GeoSpatialIndexed`, `@PersistenceConstructor`, `@Sharded` (all in `org.springframework.data.mongodb.core.mapping` / `.index`).

**Redis:** `@RedisHash`, `@TimeToLive`, `@Reference`.

**Cassandra:** `@Table`, `@PrimaryKey`, `@PrimaryKeyColumn`, `@CassandraType`.

**Elasticsearch:** `@Document`, `@Field`, `@MultiField`, `@Setting`, `@Mapping`.

**Neo4j:** `@Node`, `@Relationship`, `@Property`, `@TargetNode`, `@RelationshipProperties`.

**Repository config:** `@EnableJpaRepositories`, `@EnableMongoRepositories`, `@EnableR2dbcRepositories`, `@EnableCassandraRepositories`, `@EnableRedisRepositories`, `@EnableNeo4jRepositories`, `@EnableJdbcRepositories`, `@EnableElasticsearchRepositories`.

---

## 18. JPA / Jakarta Persistence

All in `jakarta.persistence`:

`@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@SequenceGenerator`, `@TableGenerator`, `@Column`, `@Basic`, `@Access`, `@Embedded`, `@Embeddable`, `@EmbeddedId`, `@IdClass`, `@MappedSuperclass`, `@Inheritance`, `@DiscriminatorColumn`, `@DiscriminatorValue`, `@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany`, `@JoinColumn`, `@JoinColumns`, `@JoinTable`, `@OrderBy`, `@OrderColumn`, `@MapKey`, `@MapKeyColumn`, `@ElementCollection`, `@CollectionTable`, `@Enumerated`, `@Temporal`, `@Transient`, `@Lob`, `@Version`, `@Convert`, `@Converter`, `@NamedQuery`, `@NamedQueries`, `@NamedNativeQuery`, `@NamedEntityGraph`, `@NamedAttributeNode`, `@EntityListeners`, `@PrePersist`, `@PostPersist`, `@PreUpdate`, `@PostUpdate`, `@PreRemove`, `@PostRemove`, `@PostLoad`, `@Cacheable`, `@SecondaryTable`, `@AttributeOverride`, `@AssociationOverride`.

Spring Boot:

| Annotation | Package | Purpose |
|---|---|---|
| `@EntityScan` | `org.springframework.boot.autoconfigure.domain` | Explicit base packages for JPA entities. |

---

## 19. Bean Validation (Jakarta Validation 3.1)

`jakarta.validation` / `jakarta.validation.constraints`:

`@Valid`, `@NotNull`, `@Null`, `@NotBlank`, `@NotEmpty`, `@Size`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Digits`, `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero`, `@Past`, `@PastOrPresent`, `@Future`, `@FutureOrPresent`, `@Pattern`, `@Email`, `@AssertTrue`, `@AssertFalse`.

Spring integration:

| Annotation | Package | Purpose |
|---|---|---|
| `@Validated` | `org.springframework.validation.annotation` | Spring's method-level validation trigger; supports validation groups. |

---

## 20. AOP / AspectJ

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableAspectJAutoProxy` | `org.springframework.context.annotation` | Enables AspectJ-style proxying. `proxyTargetClass`, `exposeProxy`. |
| `@Aspect` | `org.aspectj.lang.annotation` | Marks an aspect class. |
| `@Before` | same | Before advice. |
| `@After` | same | After (finally) advice. |
| `@AfterReturning` | same | After normal return. |
| `@AfterThrowing` | same | After exception. |
| `@Around` | same | Around advice. |
| `@Pointcut` | same | Named reusable pointcut. |
| `@DeclareParents` | same | Introduce an interface / mixin. |
| `@EnableLoadTimeWeaving` | `org.springframework.context.annotation` | Enables AspectJ LTW. |

---

## 21. Events

| Annotation | Package | Purpose |
|---|---|---|
| `@EventListener` | `org.springframework.context.event` | Method event listener; supports SpEL `condition`. |
| `@TransactionalEventListener` | `org.springframework.transaction.event` | TX-phase-aware listener. |
| `@Async` + `@EventListener` | `org.springframework.scheduling.annotation` | Fire listener on task executor. |
| `@Order` | `org.springframework.core.annotation` | Listener ordering. |

---

## 22. Scheduling & Async

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableScheduling` | `org.springframework.scheduling.annotation` | Enables cron/fixed scheduling. |
| `@Scheduled` | same | `cron`, `fixedRate`, `fixedDelay`, `initialDelay`, `zone`, `timeUnit`, `scheduler`. |
| `@Schedules` | same | Container for multiple `@Scheduled`. |
| `@EnableAsync` | same | Enables async proxying. |
| `@Async` | same | Execute on `TaskExecutor`. Optional qualifier. |

---

## 23. Caching

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableCaching` | `org.springframework.cache.annotation` | Enables declarative caching. |
| `@Cacheable` | same | Cache return value. `key`, `condition`, `unless`, `sync`. |
| `@CachePut` | same | Update cache (always invokes). |
| `@CacheEvict` | same | Remove entry / clear cache (`allEntries`, `beforeInvocation`). |
| `@Caching` | same | Group multiple cache ops. |
| `@CacheConfig` | same | Class-level defaults. |

---

## 24. Resilience (Spring 7 core)

Absorbed from Spring Retry. All in `org.springframework.core.retry` / `org.springframework.core.concurrent` packages.

| Annotation | Purpose |
|---|---|
| `@EnableResilientMethods` | Activates the resilience annotation infrastructure (registers the concurrency-throttling interceptor and retry interceptor). |
| `@Retryable` | Declarative retry. Attributes: `includes`, `excludes`, `maxAttempts` / `maxRetries`, `delay`, `jitter`, `multiplier`, `maxDelay`. Automatically decorates reactive return types with Reactor `Retry`. |
| `@ConcurrencyLimit` | Declarative bulkhead / concurrency cap (particularly valuable with virtual threads). |

Legacy `spring-retry` annotations (still valid if you keep the library): `@EnableRetry`, `@Retryable`, `@Recover`, `@Backoff`, `@CircuitBreaker`.

```java
@Service
class ExternalApiService {

    @Retryable(includes = GatewayTimeoutException.class,
               maxRetries = 4, delay = 200, multiplier = 2.0, jitter = 100)
    @ConcurrencyLimit(15)
    public ApiResponse call(String key) { ... }
}
```

---

## 25. Null-Safety — JSpecify

**Spring 7 deprecates `org.springframework.lang.*` null annotations** in favor of JSpecify. JSpecify annotations are `@Target(TYPE_USE)` — they apply to type usage, so they sit directly before the type.

| Annotation | Package | Purpose |
|---|---|---|
| `@NullMarked` | `org.jspecify.annotations` | Package / class / method scope: unannotated type usage is non-null by default. |
| `@NullUnmarked` | `org.jspecify.annotations` | Opt out of a surrounding `@NullMarked` scope. |
| `@Nullable` | `org.jspecify.annotations` | A particular type usage may be `null`. |
| `@NonNull` | `org.jspecify.annotations` | Explicitly non-null (rarely needed inside `@NullMarked`). |

**Deprecated** (still present for compatibility, do not use in new code): `org.springframework.lang.Nullable`, `org.springframework.lang.NonNull`, `org.springframework.lang.NonNullApi`, `org.springframework.lang.NonNullFields`.

```java
// package-info.java
@NullMarked
package com.example.api;

public interface UserRepository {
    @Nullable User findByEmail(String email);   // may be null
    List<User> findAll();                       // non-null (and elements non-null)
    User save(User user);                       // everything non-null
}
```

---

## 26. AOT / Native Image Hints

| Annotation | Package | Purpose |
|---|---|---|
| `@ImportRuntimeHints` | `org.springframework.context.annotation` | Registers a `RuntimeHintsRegistrar`. |
| `@RegisterReflectionForBinding` | `org.springframework.aot.hint.annotation` | Register type(s) for reflective binding (Jackson, validation). |
| `@RegisterReflection` | `org.springframework.aot.hint.annotation` | Fine-grained reflection hints (`memberCategories`). |
| `@Reflective` | `org.springframework.aot.hint.annotation` | Mark code as accessed reflectively; processors contribute hints. |
| `@ReflectiveRuntimeHintsRegistrar` | companion API | Combined use. |

---

## 27. Observability — Micrometer

| Annotation | Package | Purpose |
|---|---|---|
| `@Observed` | `io.micrometer.observation.annotation` | Method-level observation (metric + span). |
| `@Timed` | `io.micrometer.core.annotation` | Timer metric. |
| `@Counted` | same | Counter metric. |
| `@NewSpan` | `io.micrometer.tracing.annotation` | Create a new tracing span. |
| `@ContinueSpan` | same | Annotate the existing span. |
| `@SpanTag` | same | Add tag derived from method argument. |

Spring enabler: `@EnableObservability` (Boot auto-configured — rarely declared).

---

## 28. Actuator Endpoints

| Annotation | Package | Purpose |
|---|---|---|
| `@Endpoint` | `org.springframework.boot.actuate.endpoint.annotation` | Technology-agnostic endpoint. |
| `@WebEndpoint` | same | Web (HTTP) exposure only. |
| `@JmxEndpoint` | same | JMX-only. |
| `@ServletEndpoint` | same | Servlet-based (niche). |
| `@ControllerEndpoint` | same | MVC controller-backed endpoint. |
| `@RestControllerEndpoint` | same | REST variant. |
| `@EndpointExtension` | same | Extends an existing endpoint. |
| `@EndpointWebExtension` | same | Web-only extension. |
| `@EndpointJmxExtension` | same | JMX-only extension. |
| `@ReadOperation` | same | `GET`-like operation. |
| `@WriteOperation` | same | `POST`-like operation. |
| `@DeleteOperation` | same | `DELETE`-like operation. |
| `@Selector` | same | Operation parameter used in path. |

---

## 29. Spring Security 7

**Configuration:**

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableWebSecurity` | `org.springframework.security.config.annotation.web.configuration` | Core web security enabler. `debug` flag. |
| `@EnableMethodSecurity` | `org.springframework.security.config.annotation.method.configuration` | Modern method security. `prePostEnabled`, `securedEnabled`, `jsr250Enabled`. **Replaces** `@EnableGlobalMethodSecurity`. |
| `@EnableReactiveMethodSecurity` | same | Reactive variant. |
| `@EnableWebFluxSecurity` | `org.springframework.security.config.annotation.web.reactive` | Reactive web security. |

**Authorization:**

| Annotation | Package | Purpose |
|---|---|---|
| `@PreAuthorize` | `org.springframework.security.access.prepost` | Pre-invocation SpEL authorization. |
| `@PostAuthorize` | same | Post-invocation authorization (can inspect `returnObject`). |
| `@PreFilter` | same | Filter method argument collection before invocation. |
| `@PostFilter` | same | Filter return collection after invocation. |
| `@Secured` | `org.springframework.security.access.annotation` | Role-based (list of authorities). |
| `@RolesAllowed` | `jakarta.annotation.security` | JSR-250 roles. |
| `@PermitAll`, `@DenyAll` | same | JSR-250 open/closed. |
| `@AuthorizeReturnObject` | `org.springframework.security.authorization.method` | Recursively authorizes methods on the returned object. |

**Authentication / argument resolvers:**

| Annotation | Package | Purpose |
|---|---|---|
| `@AuthenticationPrincipal` | `org.springframework.security.core.annotation` | Inject current `UserDetails` / principal. |
| `@CurrentSecurityContext` | same | Inject `SecurityContext` or SpEL-extracted value. |
| `@RegisteredOAuth2AuthorizedClient` | `org.springframework.security.oauth2.client.annotation` | Inject authorized OAuth2 client. |
| `@ClientRegistrationId` | `org.springframework.security.oauth2.client.annotation` | On `@HttpExchange` methods, selects the OAuth2 client registration. **Recognized in Spring Security 7 for HTTP service groups.** |

**Testing:**

| Annotation | Package | Purpose |
|---|---|---|
| `@WithMockUser` | `org.springframework.security.test.context.support` | Simulated authenticated user. |
| `@WithUserDetails` | same | Load from `UserDetailsService`. |
| `@WithAnonymousUser` | same | Explicit anonymous. |
| `@WithSecurityContext` | same | Meta-annotation factory. |

---

## 30. Testing

**Core Spring Test:**

| Annotation | Package | Purpose |
|---|---|---|
| `@SpringJUnitConfig` | `org.springframework.test.context.junit.jupiter` | Composes `@ExtendWith(SpringExtension.class)` + `@ContextConfiguration`. |
| `@SpringJUnitWebConfig` | same | Web variant. |
| `@ContextConfiguration` | `org.springframework.test.context` | Loads ApplicationContext for tests. |
| `@ContextHierarchy` | same | Parent/child context hierarchy. |
| `@BootstrapWith` | same | Custom bootstrapper. |
| `@ActiveProfiles` | same | Sets active profiles. |
| `@TestPropertySource` | same | Inline / file property overrides. |
| `@DynamicPropertySource` | same | Runtime property population (Testcontainers). |
| `@DirtiesContext` | same | Mark context dirty. |
| `@Sql` | `org.springframework.test.context.jdbc` | Run SQL scripts. |
| `@SqlGroup` | same | Multiple `@Sql`. |
| `@SqlConfig` | same | Script config. |
| `@SqlMergeMode` | same | Merge inherited + local scripts. |
| `@TestExecutionListeners` | `org.springframework.test.context` | Customize test listeners. |
| `@Commit` | `org.springframework.test.annotation` | Commit TX in tests. |
| `@Rollback` | same | Roll back TX (default). |
| `@Repeat` | same | Repeat a test. |
| `@Timed` | same | Time assertion. |
| `@IfProfileValue` | same | Conditional profile value execution. |
| `@NestedTestConfiguration` | `org.springframework.test.context` | Configure JUnit 5 nested test config inheritance. |
| `@DisabledInAotMode` | `org.springframework.test.context.aot` | Skip test in AOT-processed mode. |
| `@RecordApplicationEvents` | `org.springframework.test.context.event` | Record events published during tests. |
| `@TestConstructor` | `org.springframework.test.context` | Autowire test constructor args. |

**Spring Boot Test:**

| Annotation | Package | Purpose |
|---|---|---|
| `@SpringBootTest` | `org.springframework.boot.test.context` | Full-context integration test. `webEnvironment`, `classes`, `properties`. |
| `@TestConfiguration` | same | Test-only `@Configuration`. |
| `@TestComponent` | same | Test-only `@Component`. |
| `@MockBean` *(deprecated in Boot 3.4 / removed direction)* | `org.springframework.boot.test.mock.mockito` | Replace bean with a Mockito mock. **Prefer `@MockitoBean`.** |
| `@SpyBean` *(deprecated)* | same | Replace with a Mockito spy. **Prefer `@MockitoSpyBean`.** |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` | Framework-neutral replacement for `@MockBean`. |
| `@MockitoSpyBean` | same | Replacement for `@SpyBean`. |
| `@TestBean` | `org.springframework.test.context.bean.override.convention` | Override a bean with the test-defined factory method. |
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | Auto-configure MockMvc. |
| `@AutoConfigureWebTestClient` | `org.springframework.boot.test.autoconfigure.web.reactive` | Reactive. |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.test.autoconfigure.jdbc` | Replace `DataSource` (H2, Testcontainers). |
| `@AutoConfigureJsonTesters` | `org.springframework.boot.test.autoconfigure.json` | Jackson/Gson/Jsonb testers. |
| `@AutoConfigureRestDocs` | `org.springframework.boot.test.autoconfigure.restdocs` | Spring REST Docs. |
| `@AutoConfigureJooq` | `org.springframework.boot.test.autoconfigure.jooq` | jOOQ. |
| `@AutoConfigureDataJpa`, `@AutoConfigureDataMongo`, `@AutoConfigureDataRedis`, `@AutoConfigureDataNeo4j`, `@AutoConfigureDataCassandra`, `@AutoConfigureDataLdap`, `@AutoConfigureDataElasticsearch`, `@AutoConfigureDataR2dbc`, `@AutoConfigureDataCouchbase` | various | Data slice configuration. |
| `@AutoConfigureObservability` | `org.springframework.boot.test.autoconfigure.observability` | Observability in tests. |
| `@AutoConfigureHttpGraphQlTester` | `org.springframework.boot.test.autoconfigure.graphql.tester` | GraphQL tester. |

**Test Slices:**

| Annotation | Package | Purpose |
|---|---|---|
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | MVC-only slice. |
| `@WebFluxTest` | `org.springframework.boot.test.autoconfigure.web.reactive` | Reactive slice. |
| `@DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa` | JPA + in-memory DB. |
| `@DataMongoTest` | `org.springframework.boot.test.autoconfigure.data.mongo` | Mongo slice. |
| `@DataRedisTest` | same | Redis slice. |
| `@DataNeo4jTest`, `@DataCassandraTest`, `@DataCouchbaseTest`, `@DataLdapTest`, `@DataElasticsearchTest`, `@DataR2dbcTest`, `@DataJdbcTest` | `org.springframework.boot.test.autoconfigure.data.*` / `.jdbc` | Store-specific slices. |
| `@JsonTest` | `org.springframework.boot.test.autoconfigure.json` | JSON mappers only. |
| `@RestClientTest` | `org.springframework.boot.test.autoconfigure.web.client` | `RestTemplate` / `RestClient` testing. |
| `@GraphQlTest` | `org.springframework.boot.test.autoconfigure.graphql` | GraphQL controllers. |
| `@JooqTest` | `org.springframework.boot.test.autoconfigure.jooq` | jOOQ slice. |
| `@WebServiceServerTest` | `org.springframework.boot.test.autoconfigure.webservices.server` | Spring-WS server slice. |
| `@WebServiceClientTest` | `org.springframework.boot.test.autoconfigure.webservices.client` | Spring-WS client slice. |

---

## 31. Spring Cloud (Portfolio Highlights)

Spring Cloud is a separate portfolio with its own release train, but commonly used with Spring Boot:

| Annotation | Module | Purpose |
|---|---|---|
| `@EnableDiscoveryClient` | `spring-cloud-commons` | Register with discovery (Consul, Eureka, Nacos, Zookeeper, K8s). |
| `@EnableFeignClients` / `@FeignClient` | `spring-cloud-openfeign` | Declarative HTTP client (legacy — prefer `@HttpExchange` in new code). |
| `@LoadBalanced` | `spring-cloud-commons` | Mark `RestTemplate`/`RestClient`/`WebClient.Builder` for client-side LB. |
| `@RefreshScope` | `spring-cloud-context` | Bean re-created on config refresh. |
| `@EnableConfigServer` | `spring-cloud-config-server` | Activate Config Server. |
| `@SpringCloudApplication` | `spring-cloud-commons` | Meta: `@SpringBootApplication` + discovery. |
| `@EnableBinding`, `@Input`, `@Output`, `@StreamListener` (legacy) | `spring-cloud-stream` | Message channels. Modern Stream uses functional model. |
| `@EnableTask` | `spring-cloud-task` | Short-lived task apps. |
| `@EnableContractStubRunner` | `spring-cloud-contract` | Contract testing. |

---

## 32. Spring Batch & Integration

**Spring Batch:**

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableBatchProcessing` | `org.springframework.batch.core.configuration.annotation` | Enables batch infrastructure. |
| `@JobScope` / `@StepScope` | same | Batch bean scopes. |
| `@BeforeJob`, `@AfterJob`, `@BeforeStep`, `@AfterStep`, `@BeforeChunk`, `@AfterChunk`, `@BeforeRead`, `@AfterRead`, `@OnReadError`, `@BeforeProcess`, `@AfterProcess`, `@OnProcessError`, `@BeforeWrite`, `@AfterWrite`, `@OnWriteError`, `@OnSkipInRead`, `@OnSkipInProcess`, `@OnSkipInWrite` | `org.springframework.batch.core.annotation` | Listener methods. |

**Spring Integration:**

| Annotation | Package | Purpose |
|---|---|---|
| `@EnableIntegration` | `org.springframework.integration.config` | Enables SI. |
| `@MessagingGateway` | `org.springframework.integration.annotation` | Declarative gateway interface. |
| `@ServiceActivator` | same | Message consumer. |
| `@Router` | same | Router. |
| `@Splitter` | same | Split a message. |
| `@Aggregator` | same | Aggregate messages. |
| `@Filter` | same | Filter. |
| `@Transformer` | same | Transform payload. |
| `@InboundChannelAdapter` | same | Inbound polling adapter. |
| `@Publisher` | same | Publish method return value. |
| `@BridgeFrom` / `@BridgeTo` | same | Channel bridges. |
| `@IntegrationComponentScan` | `org.springframework.integration.config` | Scan SI annotations (gateways etc). |
| `@Payload`, `@Header`, `@Headers` | `org.springframework.messaging.handler.annotation` | Argument binding. |

---

## 33. Lesser-Known / Meta Annotations

| Annotation | Package | Purpose |
|---|---|---|
| `@AliasFor` | `org.springframework.core.annotation` | Create attribute aliases inside composed annotations. |
| `@Experimental` / `@Deprecated` (Spring uses standard `java.lang.Deprecated`) | — | — |
| `@GraphQlRepository` | `org.springframework.graphql.data` | Expose Spring Data repository to GraphQL. |
| `@SchemaMapping` | `org.springframework.graphql.data.method.annotation` | GraphQL field resolver. |
| `@QueryMapping` | same | Query-type shortcut. |
| `@MutationMapping` | same | Mutation-type shortcut. |
| `@SubscriptionMapping` | same | Subscription-type shortcut. |
| `@BatchMapping` | same | Batched loader. |
| `@Argument` | same | Argument binding. |
| `@ContextValue` | same | GraphQL context binding. |
| `@ProjectedPayload` | `org.springframework.data.web` | Spring Data REST projection. |
| `@EnableSpringDataWebSupport` | `org.springframework.data.web.config` | Pageable/Sort argument resolvers. |
| `@PageableDefault` | `org.springframework.data.web` | Default pagination. |
| `@SortDefault` | same | Default sort. |
| `@Tag` | `io.swagger.v3.oas.annotations.tags` | OpenAPI (springdoc) — not Spring core, but universal in REST apps. |
| `@SessionAttributes` (Spring Session) | `org.springframework.session.web.http` | Session attribute handling (Spring Session project). |
| `@EnableRedisHttpSession` | `org.springframework.session.data.redis.config.annotation.web.http` | Distributed HTTP session. |
| `@EnableJdbcHttpSession`, `@EnableMongoHttpSession`, `@EnableHazelcastHttpSession` | respective packages | Session stores. |
| `@EnableSpringConfigured` | `org.springframework.context.annotation` | Domain object dependency injection via AspectJ. |
| `@Configurable` | `org.springframework.beans.factory.annotation` | Marks class for `@EnableSpringConfigured` injection. |
| `@Import` of `BeanRegistrar` | `org.springframework.beans.factory.BeanRegistrar` | **New in Spring 7** programmatic, profile-aware bean registration. |
| `@ReflectiveConstructor` / `@ReflectiveField` | `org.springframework.aot.hint.annotation` | Fine-grained reflective hints. |
| `@Polyfill` annotations — none. Keep watching spring-projects releases. |

---

## Quick Reference — What's Genuinely New in Spring 7 / Boot 4

| Area | New |
|---|---|
| **API versioning** | `version` attribute on `@RequestMapping` / `@GetMapping` / `@PostMapping` / etc.; functional `version("…")` predicate. |
| **HTTP clients** | `@ImportHttpServices`, group-based HTTP service registry. |
| **Resilience** | `@EnableResilientMethods`, `@Retryable` and `@ConcurrencyLimit` moved into `spring-core`. `@Retryable` automatically decorates reactive return types. |
| **Null-safety** | Migration from `org.springframework.lang.*` to JSpecify (`@NullMarked`, `@Nullable`, `@NonNull`, `@NullUnmarked`). |
| **Security + HTTP clients** | `@ClientRegistrationId` detected on `@HttpExchange` methods for OAuth2. |
| **Testing** | `@MockitoBean`, `@MockitoSpyBean`, `@TestBean` (formally the bean-override replacements for `@MockBean`/`@SpyBean`). |
| **Bean registration** | `BeanRegistrar` (imported via `@Import`) — programmatic, profile-aware. |
| **Auto-config** | Modularized into multiple JARs (annotations unchanged). |

---

### Sources / Further Reading

- Spring Framework 7.0 / 7.1 Release Notes (GitHub wiki)
- Spring Framework reference: Null-Safety, Method Security, HTTP Service Client Enhancements
- Spring Boot 4.0 release blog
- Jakarta EE 11, Jakarta Validation 3.1, Jakarta Persistence 3.2 specs
- JSpecify documentation (`jspecify.dev`)
