Now we move from the "empty bucket" stage to the **Search and Discovery** phase. If Step 1 was building the library, **Step 2: Component Scanning** is the librarian going through every room to find the books that need to be indexed.

---

### Step 2: Component Scanning (Discovery)

In this phase, Spring uses a **`ClassPathBeanDefinitionScanner`** to look through your project’s folders for any class that has a "Spring Sign" (an annotation) on it.

#### 1. The Starting Point (The Base Package)
Spring Boot starts its search at the package where your `@SpringBootApplication` class is located.
* It recursively looks into every sub-package.
* This is why it’s a standard rule to put your main class in a top-level package like `com.myapp`. If you put a service in `com.otherapp`, Spring will be "blind" to it unless you explicitly tell it to look there.

#### 2. The "Stereotype" Annotations
The scanner is specifically looking for "Stereotypes." Think of these as labels that tell Spring, "Hey, I’m a bean! Manage me!"

| Annotation | The "Librarian's" Interpretation |
| :--- | :--- |
| **`@Component`** | The most basic label. "This is a bean." |
| **`@Service`** | "This is a bean that handles business logic." |
| **`@Repository`** | "This is a bean that talks to a database" (adds extra error handling). |
| **`@Controller`** | "This is a bean that handles web requests." |
| **`@Configuration`** | "This is a bean that *contains* other bean definitions (via `@Bean`)." |



#### 3. Handling `@Configuration` and `@Bean`
When the scanner hits your `AppConfig` class, it doesn't just register the class itself. It peeks inside and sees your methods marked with `@Bean`. It notes down:
* "I found a method called `dataSource`. It says it returns a `DataSource`."
* "I found a method called `jdbcTemplate`. It says it needs a `DataSource`."

---

### The Outcome: The "Incomplete" Catalog
By the end of this step, Spring has a list of **Bean Definitions**. However, it hasn't actually called `new OrderService()` yet. It has just gathered the "blueprints."

**Why this is clever:** By finding everything *before* creating anything, Spring can detect problems early—like if you have two beans with the exact same name, or if a class is trying to `@Autowire` something that doesn't exist.

> **Fun Fact:** This is why your IDE (like IntelliJ) can sometimes show you a red underline on an `@Autowired` field before you even run the app. It's simulating this "Discovery" phase to warn you of a missing link!

---