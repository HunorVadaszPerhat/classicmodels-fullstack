
---

## Section 4 — Practice Questions

---

**Question 1** (Single answer)

A developer annotates a test class with `@WebMvcTest(OrderController.class)`. The `OrderController` depends on `OrderService`, which depends on `OrderRepository`. What must the developer do to make the test context start successfully?

A) Annotate `OrderService` and `OrderRepository` with `@MockBean`  
B) Add `@SpringBootTest` alongside `@WebMvcTest` to load the full context  
C) Add `@ComponentScan` to the test class to pick up service and repository beans  
D) Annotate `OrderService` with `@MockBean` — Spring will auto-mock `OrderRepository` as a transitive dependency

---

**Question 2** (Single answer)

A test class is annotated with `@SpringBootTest(webEnvironment = RANDOM_PORT)`. A test method calls `testRestTemplate.postForEntity("/orders", order, Order.class)` to create an order. After the test method completes, is the created order still in the database?

A) No — `@SpringBootTest` rolls back all transactions automatically  
B) Yes — `RANDOM_PORT` tests do not roll back automatically; the HTTP request commits its transaction  
C) No — `TestRestTemplate` wraps each request in a transaction that rolls back  
D) Yes — but only if `@Commit` is added to the test method

---

**Question 3** (Multiple answer — Choose 2)

Which two statements correctly describe the difference between `@Mock` and `@MockBean`?

A) `@Mock` requires a Spring application context to function  
B) `@MockBean` registers the mock as a bean in the Spring application context  
C) `@Mock` and `@MockBean` both invalidate the Spring context cache when used  
D) `@MockBean` replaces an existing bean of the same type in the Spring context  
E) `@Mock` can be used in `@SpringBootTest` tests to replace Spring-managed beans

---

**Question 4** (Single answer)

A developer wants to verify that a custom `@JsonComponent` serialises an `Order` object correctly — specifically that an internal audit field annotated with `@JsonIgnore` does not appear in the JSON output. Which test annotation is most appropriate?

A) `@SpringBootTest`  
B) `@WebMvcTest`  
C) `@DataJpaTest`  
D) `@JsonTest`

---

**Question 5** (Multiple answer — Choose 3)

Which three of the following statements about `@TestConfiguration` are correct?

A) A nested static `@TestConfiguration` class is automatically picked up by the enclosing test class  
B) A top-level `@TestConfiguration` class is automatically picked up by all test classes in the same package  
C) A top-level `@TestConfiguration` class must be explicitly imported with `@Import`  
D) `@TestConfiguration` beans are added to (or override) the Spring context for tests only  
E) `@TestConfiguration` can only be used with `@SpringBootTest` — not with slice annotations  
F) `@TestConfiguration` is a specialisation of `@Configuration` intended for test use

---

Take your time and answer all five. I'll explain every option when you're ready.