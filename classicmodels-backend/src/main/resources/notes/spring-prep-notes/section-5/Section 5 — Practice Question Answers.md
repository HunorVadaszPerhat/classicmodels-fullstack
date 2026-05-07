## Section 5 — Practice Question Answers

---

**Question 1 — Answer: B**

`authorizeRequests()` rules are evaluated in order — the first matching rule wins. `anyRequest().authenticated()` is placed first, so it matches every request including `/public/home` before the `permitAll()` rule is ever reached. The `/public/**` and `/admin/**` rules below it are never evaluated. The unauthenticated user is denied access and redirected to login. This is exactly why the catch-all `anyRequest()` must always be last.

- **A** — Wrong. The `permitAll()` rule exists but is never reached because `anyRequest()` matched first.
- **C** — Wrong. Spring does not throw at startup for this ordering mistake — it's a silent runtime bug, not a startup error. The rules are syntactically valid; the problem is purely logical ordering.
- **D** — Wrong. Spring Security has no concept of "always allow public paths" based on naming. Security decisions are made purely by the rules you configure, in the order you configure them.

---

**Question 2 — Answer: B**

`hasRole('ADMIN')` is correct. Spring Security automatically prepends `ROLE_` when using `hasRole()`, so `hasRole('ADMIN')` matches a `GrantedAuthority` of `ROLE_ADMIN`. This is the cleanest and most idiomatic way to check roles in `@PreAuthorize`.

- **A** — Wrong. `hasRole('ROLE_ADMIN')` causes a double-prefix problem — Spring prepends `ROLE_` automatically, so it looks for `ROLE_ROLE_ADMIN`. This will never match and is a common mistake.
- **C** — Wrong. `hasAuthority('ADMIN')` looks for an exact `GrantedAuthority` string of `ADMIN` — no prefix added. This won't match `ROLE_ADMIN`. You'd need `hasAuthority('ROLE_ADMIN')` for this to work.
- **D** — Wrong on two counts. `@Secured` requires the full string including `ROLE_` prefix — so it should be `@Secured("ROLE_ADMIN")`. Additionally `@Secured("ADMIN")` without the prefix would never match. And `@Secured` is not `@PreAuthorize` — the question asks specifically about `@PreAuthorize`.

---

**Question 3 — Answers: B and C**

- **B** ✅ — `@PostAuthorize` is a post-execution check. The method runs to completion first, produces a return value, and then the SpEL expression is evaluated. If the check fails, `AccessDeniedException` is thrown after the method has already executed.
- **C** ✅ — `returnObject` is the special SpEL variable that holds the method's return value inside a `@PostAuthorize` expression. This is what makes `@PostAuthorize` useful — you can make authorisation decisions based on what the method actually returned.
- **A** — Wrong. This describes `@PreAuthorize`. `@PostAuthorize` does not prevent execution — it runs after execution and discards the return value if the check fails.
- **D** — Wrong. `@PostAuthorize` is part of the pre/post annotation family and requires `prePostEnabled = true` — not `securedEnabled = true`. `securedEnabled` enables `@Secured` only.
- **E** — Wrong. `@PostAuthorize` throws `AccessDeniedException` after the method executes but does not automatically roll back database changes. Rollback only happens if the method is also `@Transactional` and the exception causes that transaction to roll back — which is a separate concern and not guaranteed.

---

**Question 4 — Answer: B**

Method security is implemented via AOP proxies. When a method calls another method in the same class, the call goes directly to `this` — bypassing the proxy entirely. The `@PreAuthorize` check on `deleteOrder()` is silently ignored. The method executes without any security enforcement regardless of the caller's roles. This is the self-invocation limitation from 1.6.2 applying directly to method security.

- **A** — Wrong. The security check never fires — the proxy is bypassed before the check can be evaluated.
- **C** — Wrong. The question doesn't mention private methods. Self-invocation of public methods is the issue here. Spring doesn't throw `IllegalStateException` for this — it silently bypasses the security check.
- **D** — Wrong. Spring Security method security uses the same AOP proxy mechanism as other Spring AOP features — specifically `BeanPostProcessor`-based proxy creation. It has exactly the same self-invocation limitation. There is no special proxy mechanism that circumvents this.

---

**Question 5 — Answers: A, C, and D**

- **A** ✅ — `WebSecurityConfigurerAdapter` uses `antMatchers()` for URL pattern matching. The modern `SecurityFilterChain` approach uses `requestMatchers()`. This is one of the clearest API differences between the two approaches and is in your version reference table.
- **C** ✅ — `WebSecurityConfigurerAdapter` configures in-memory authentication by overriding `configure(AuthenticationManagerBuilder auth)` and calling `auth.inMemoryAuthentication()`. The modern approach declares a `UserDetailsService` bean — typically an `InMemoryUserDetailsManager` — which Spring Security auto-detects.
- **D** ✅ — The modern approach uses `authorizeHttpRequests()` with `requestMatchers()`. The exam-era approach uses `authorizeRequests()` with `antMatchers()`. Both configure the same concept — URL-based authorisation rules — but through different method names.
- **B** — Wrong. The modern approach does not extend `SecurityFilterChain` — `SecurityFilterChain` is an interface, not a class to extend. You declare a `@Bean` method that returns a `SecurityFilterChain` instance built from `HttpSecurity`. No inheritance involved.
- **E** — Wrong. `WebSecurityConfigurerAdapter` was deprecated in Spring Security 5.7 (Boot 2.7) and removed in Spring Security 6.0 (Boot 3.0) — not in Spring Security 5.0. The exam was written against Boot 2.5 / Spring Security 5.5 where it was still the primary approach.
- **F** — Technically true — both approaches use `HttpSecurity` to configure HTTP rules. However the question asks for differences, not similarities. F describes a commonality, not a difference, so it doesn't belong in the answer set.

---

Ready to start **Section 6 — Spring Boot**, beginning with **6.1.1** (core features, `@SpringBootApplication`, runners, fat JAR) when you give the OK.