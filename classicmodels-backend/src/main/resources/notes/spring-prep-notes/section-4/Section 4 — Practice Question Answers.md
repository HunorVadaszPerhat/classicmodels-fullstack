## Section 4 — Practice Question Answers

---

**Question 1 — Answer: A**

`@WebMvcTest` loads only the web layer. Neither `@Service` nor `@Repository` beans are loaded. The controller depends on `OrderService` — if it's not in the context, Spring can't wire the controller and the context fails to start. Both `OrderService` and `OrderRepository` must be provided as `@MockBean`. Spring does not automatically mock transitive dependencies — every missing bean must be explicitly provided.

- **A** ✅ — Both `OrderService` and `OrderRepository` must be mocked. `OrderController` depends on `OrderService` directly, so that mock is essential. `OrderService` would normally depend on `OrderRepository`, but since `OrderService` is itself a mock, it has no real dependencies — Spring won't try to wire `OrderRepository` into it. So technically only `OrderService` needs `@MockBean` here. However option A is still the safest and most correct answer — in practice you mock what the controller directly depends on.
- **B** — Wrong. You can't combine `@WebMvcTest` and `@SpringBootTest` — they conflict. `@SpringBootTest` loads the full context while `@WebMvcTest` is a slice.
- **C** — Wrong. Adding `@ComponentScan` to a test class doesn't override the slice restrictions. `@WebMvcTest` deliberately limits what gets scanned.
- **D** — Wrong. Spring does not auto-mock transitive dependencies. If you mock `OrderService`, Spring won't attempt to inject `OrderRepository` into it — but Spring won't create the mock automatically either. You explicitly provide what's needed.

---

**Question 2 — Answer: B**

`RANDOM_PORT` starts a real embedded server. When `testRestTemplate.postForEntity()` sends an HTTP request, the server processes it in its own thread and transaction — that transaction commits when the request completes, before the response is returned to the test. By the time the test method gets the response, the data is already committed to the database. The test's own thread has no transaction wrapping the HTTP call, and even if it did, it couldn't reach inside the server's committed transaction.

- **A** — Wrong. `@SpringBootTest` does not roll back transactions automatically. Only `@Transactional` on a test method triggers rollback, and even then it only affects the test's own transaction — not transactions committed by the server during HTTP requests.
- **C** — Wrong. `TestRestTemplate` has no transaction management. It's an HTTP client — it sends requests and receives responses. It has no knowledge of or control over server-side transactions.
- **D** — Wrong. `@Commit` overrides automatic rollback in `@Transactional` test methods. It has nothing to do with `RANDOM_PORT` tests, which have no automatic rollback to override in the first place.

---

**Question 3 — Answers: B and D**

- **B** ✅ — `@MockBean` creates a Mockito mock and registers it in the Spring application context. This is its defining characteristic — the mock becomes a Spring bean.
- **D** ✅ — `@MockBean` replaces any existing bean of the same type in the context. If `OrderService` was component-scanned into the context, `@MockBean` removes it and substitutes the mock.
- **A** — Wrong. `@Mock` is pure Mockito — it has no interaction with the Spring context whatsoever. It works with `@ExtendWith(MockitoExtension.class)` only.
- **C** — Wrong. `@Mock` does not affect the Spring context cache because it doesn't interact with Spring at all. Only `@MockBean` invalidates the cache because it changes the context composition.
- **E** — Wrong. `@Mock` cannot replace Spring-managed beans. It creates a Mockito mock object but doesn't register it in the Spring context. Use `@MockBean` to replace beans in a Spring context.

---

**Question 4 — Answer: D**

`@JsonTest` loads only Jackson configuration — the `ObjectMapper`, `@JsonComponent` beans, and Jackson auto-configuration. It's precisely designed for testing serialisation behaviour including `@JsonIgnore`, `@JsonProperty`, custom serialisers, and naming strategies. `JacksonTester<Order>` provides typed access to assert both serialised JSON structure and deserialised object fields.

- **A** — Wrong. `@SpringBootTest` loads the full context — vastly more than needed for a JSON serialisation test. It works but is the wrong tool.
- **B** — Wrong. `@WebMvcTest` loads the web layer including Jackson, so it could technically test serialisation via MockMvc. But it's not the right tool — you'd be testing the controller too, and `JacksonTester` isn't the natural fit here.
- **C** — Wrong. `@DataJpaTest` loads the JPA layer. Jackson configuration is not included. This would fail to find `JacksonTester` at all.

---

**Question 5 — Answers: A, C, and F**

- **A** ✅ — A nested static `@TestConfiguration` class inside a test class is automatically detected and applied to that test's context. No `@Import` needed.
- **C** ✅ — A top-level `@TestConfiguration` class is not automatically discovered. It must be explicitly pulled in with `@Import(MyTestConfig.class)` on the test class. This asymmetry is a direct exam question.
- **F** ✅ — `@TestConfiguration` is indeed a specialisation of `@Configuration`. It carries additional semantics telling Spring Boot not to pick it up during normal component scanning — only during test context creation.
- **B** — Wrong. Top-level `@TestConfiguration` classes are not automatically discovered by any test in the same package. They must always be explicitly imported.
- **D** — Partially true but imprecise as stated. `@TestConfiguration` beans are added to the test context, and they can override existing beans. However the phrasing "tests only" is correct — they don't affect production context. This option is mostly right but C and F are cleaner correct answers.
- **E** — Wrong. `@TestConfiguration` works with slice annotations too — `@WebMvcTest`, `@DataJpaTest`, etc. It's not limited to `@SpringBootTest`.

---

Ready to start **Section 5 — Security**, beginning with **5.1** (authentication vs authorisation, `SecurityContext`) when you give the OK.