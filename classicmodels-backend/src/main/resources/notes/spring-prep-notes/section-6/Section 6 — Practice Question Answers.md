## Section 6 — Practice Question Answers

---

**Question 1 — Answer: C**

Command-line arguments have the highest precedence in Spring Boot's property source order — they override everything including OS environment variables, profile-specific files, and `application.properties`. The `--server.port=9090` argument wins. OS environment variables (`SERVER_PORT=7070`) sit below command-line arguments in the precedence order. `application.properties` (`server.port=8080`) is near the bottom.

- **A** — Wrong. `application.properties` inside the JAR is near the bottom of the precedence order — it's the baseline that everything else overrides.
- **B** — Wrong. OS environment variables do override `application.properties` but they lose to command-line arguments.
- **D** — Wrong. Spring Boot has no such exception. Multiple sources defining the same property is the normal case — precedence order resolves it silently.

---

**Question 2 — Answer: C**

By default, only `health` and `info` are exposed over HTTP. All other endpoints exist but are not accessible via HTTP without explicit configuration. `GET /actuator/health` returns a successful response out of the box.

- **A** — Wrong. `/actuator/beans` is enabled but not exposed over HTTP by default. It returns 404.
- **B** — Wrong. `/actuator/env` is enabled but not exposed over HTTP by default. It returns 404.
- **D** — Wrong. `/actuator/metrics` is enabled but not exposed over HTTP by default. It returns 404.

---

**Question 3 — Answers: A and C**

- **A** ✅ — `@ConditionalOnMissingBean` checks whether a bean of the specified type already exists in the application context. If no such bean exists, the condition passes and the configuration applies.
- **C** ✅ — This is the direct consequence of A. When you declare your own bean of the same type, `@ConditionalOnMissingBean` detects it and the autoconfigured bean is skipped entirely. Your bean wins.
- **B** — Wrong. This describes `@ConditionalOnMissingClass`, not `@ConditionalOnMissingBean`. Class presence on the classpath is a completely different condition from bean presence in the context.
- **D** — Wrong. This describes something closer to `@ConditionalOnProperty` with `matchIfMissing`. `@ConditionalOnMissingBean` has no awareness of properties files.
- **E** — Wrong. `@ConditionalOnMissingBean` checks the bean registry. `@ConditionalOnMissingClass` checks the classpath. They operate on completely different things.

---

**Question 4 — Answer: C**

`Gauge` is for values that represent a current state and can go both up and down — exactly what active connection count represents. The number of active connections fluctuates as connections are opened and closed. Micrometer samples the gauge value on demand by calling the supplier function you provide, always returning the current state.

- **A** — Wrong. `Counter` only goes up — it counts cumulative events. Active connection count goes up and down, so Counter is the wrong type.
- **B** — Wrong. `Timer` measures duration and is used for timing how long operations take — not for tracking a current count.
- **D** — Wrong. `DistributionSummary` records the distribution of values over time — sizes, amounts. It's not designed for tracking a current state that fluctuates.

---

**Question 5 — Answers: A, B, D, and E**

Wait — the question asks for three. Let me be precise:

- **A** ✅ — `spring-boot-starter-parent` explicitly inherits from `spring-boot-dependencies` in its own POM. This is the two-level hierarchy — starter-parent IS-A child of dependencies.
- **B** ✅ — `spring-boot-dependencies` is a BOM (Bill of Materials) — a POM of type `pom` that declares `<dependencyManagement>` entries with specific tested-together versions. This is its entire purpose.
- **D** ✅ — For projects with corporate parent POMs that can't extend `spring-boot-starter-parent`, importing `spring-boot-dependencies` directly in `<dependencyManagement>` with `scope=import, type=pom` achieves the same version management without the parent relationship.
- **E** is also true — `spring-boot-starter-parent` provides Maven plugin defaults including compiler settings, resource filtering, and `spring-boot-maven-plugin` configuration. This is something `spring-boot-dependencies` alone does not provide.

With four valid options for a choose-3 question the intended answers are A, B, and D — these are the cleanest and most directly testable facts. E is true but is a secondary benefit that's less commonly tested. C and F are clearly wrong:

- **C** — Wrong. They are distinct artifacts with different purposes and different Maven coordinates. `spring-boot-starter-parent` is a parent POM. `spring-boot-dependencies` is a BOM. One inherits from the other.
- **F** — Wrong. `spring-boot-dependencies` is purely a BOM — it contains only `<dependencyManagement>` entries. Plugin configuration lives in `spring-boot-starter-parent`, not in the BOM.

If you selected A, B, and E — that's a defensible set. A, B, and D is the cleaner exam answer because the BOM import pattern (D) is a more directly tested exam topic than the plugin configuration detail (E).

---

That completes all six sections and all end-of-section practice questions. You've worked through the entire exam content systematically.

Given where you are, here's what I'd suggest for the final stretch based on your study prompt's pre-exam checklist:

**If the exam is more than a week away** — do a full timed mock exam (60 questions, 130 minutes) using David Archanjo's guide or JavaRevisited. Note which topics cause you to slow down — those are your drill targets.

**If the exam is within a week** — stop learning new things. Do short active recall passes on the HIGH VALUE topics: transaction propagation, prototype-into-singleton, AOP proxy limitations, `@SpringBootTest` webEnvironment options, `@ConditionalOnMissingBean`, and the property precedence order.

**The morning of the exam** — read only the QUICK REFERENCE section at the top of your study prompt. The 10 traps and the out-of-scope list. Nothing else.

Is there anything you'd like to drill further before the exam — any topic that still feels shaky?