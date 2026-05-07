
---

## Section 5 — Practice Questions

---

**Question 1** (Single answer)

A developer configures Spring Security with the following rules using the exam-style API:

```java
http.authorizeRequests()
    .anyRequest().authenticated()
    .antMatchers("/public/**").permitAll()
    .antMatchers("/admin/**").hasRole("ADMIN");
```

What happens when an unauthenticated user requests `/public/home`?

A) Access is permitted because `/public/**` is matched by `permitAll()`  
B) Access is denied because `anyRequest().authenticated()` matches first  
C) Spring throws `IllegalStateException` at startup because the rules are in the wrong order  
D) Access is permitted because unauthenticated users are always allowed to access public paths

---

**Question 2** (Single answer)

Which of the following expressions correctly allows access only to users who have the `ROLE_ADMIN` authority using `@PreAuthorize`?

A) `@PreAuthorize("hasRole('ROLE_ADMIN')")`  
B) `@PreAuthorize("hasRole('ADMIN')")`  
C) `@PreAuthorize("hasAuthority('ADMIN')")`  
D) `@Secured("ADMIN")`

---

**Question 3** (Multiple answer — Choose 2)

Which two statements about `@PostAuthorize` are correct?

A) It prevents the method from executing if the expression evaluates to false  
B) The method executes before the authorisation check is evaluated  
C) The return value of the method is accessible via the `returnObject` variable in the SpEL expression  
D) It requires `securedEnabled = true` to be set in `@EnableGlobalMethodSecurity`  
E) It always rolls back any database changes made by the method when access is denied

---

**Question 4** (Single answer)

A developer annotates a `@Service` method with `@PreAuthorize("hasRole('ADMIN')")`. Another method in the same service class calls this method directly. An authenticated user with `ROLE_USER` calls the outer method. What happens?

A) `AccessDeniedException` is thrown because the user lacks `ROLE_ADMIN`  
B) The `@PreAuthorize` check is bypassed due to self-invocation — the method executes without security check  
C) Spring throws `IllegalStateException` because method security cannot be used on private methods  
D) The security check is applied because Spring Security uses a different proxy mechanism than AOP

---

**Question 5** (Multiple answer — Choose 3)

Which three of the following correctly describe differences between the exam-era `WebSecurityConfigurerAdapter` approach and the modern `SecurityFilterChain` bean approach?

A) `WebSecurityConfigurerAdapter` uses `antMatchers()` while the modern approach uses `requestMatchers()`  
B) The modern approach requires extending `SecurityFilterChain` instead of `WebSecurityConfigurerAdapter`  
C) `WebSecurityConfigurerAdapter` configures in-memory users via `AuthenticationManagerBuilder` while the modern approach uses a `UserDetailsService` bean  
D) The modern approach uses `authorizeHttpRequests()` while `WebSecurityConfigurerAdapter` uses `authorizeRequests()`  
E) `WebSecurityConfigurerAdapter` was removed in Spring Security 5.0  
F) Both approaches use `HttpSecurity` to configure HTTP security rules

---

Take your time and answer all five. I'll explain every option when you're ready.