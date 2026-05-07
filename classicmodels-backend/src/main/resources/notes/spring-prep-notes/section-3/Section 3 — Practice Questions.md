
---

## Section 3 — Practice Questions

---

**Question 1** (Single answer)

Which of the following correctly describes what `@SpringBootApplication` is composed of?

A) `@Configuration` + `@ComponentScan` + `@EnableTransactionManagement`  
B) `@Component` + `@EnableAutoConfiguration` + `@ComponentScan`  
C) `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`  
D) `@Configuration` + `@EnableAutoConfiguration` + `@EnableAspectJAutoProxy`

---

**Question 2** (Single answer)

A `@RestController` method returns an `Order` object directly (not wrapped in `ResponseEntity`). The service layer throws an unchecked exception for a missing order. What HTTP status code does the client receive if no `@ExceptionHandler` is configured?

A) 404 Not Found  
B) 400 Bad Request  
C) 500 Internal Server Error  
D) 200 OK with a null body

---

**Question 3** (Multiple answer — Choose 2)

Which two statements about `@RestControllerAdvice` are correct?

A) It is equivalent to `@ControllerAdvice` + `@ResponseBody`  
B) It is equivalent to `@Controller` + `@ResponseBody`  
C) A controller-local `@ExceptionHandler` takes precedence over a `@RestControllerAdvice` handler for the same exception type  
D) `@RestControllerAdvice` only handles exceptions from `@RestController` classes, not `@Controller` classes  
E) Methods annotated with `@ExceptionHandler` inside `@RestControllerAdvice` must return `ResponseEntity`

---

**Question 4** (Single answer)

A developer needs to make a GET request using `RestTemplate` and receive a `List<Order>` from a JSON array response. Which approach correctly deserialises the generic type?

A) `restTemplate.getForObject(url, List.class)`  
B) `restTemplate.getForObject(url, List<Order>.class)`  
C) `restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<List<Order>>() {})`  
D) `restTemplate.getForEntity(url, List.class)`

---

**Question 5** (Multiple answer — Choose 3)

Which three of the following are required to correctly deploy a Spring Boot application as a WAR to an external servlet container?

A) Set `<packaging>war</packaging>` in `pom.xml`  
B) Remove the `spring-boot-maven-plugin` from `pom.xml`  
C) Extend `SpringBootServletInitializer` and override `configure()`  
D) Mark the embedded Tomcat dependency as `provided` scope  
E) Remove the `main()` method from the application class  
F) Add `@EnableWebMvc` to the application class

---

Take your time and answer all five. I'll explain every option when you're ready.