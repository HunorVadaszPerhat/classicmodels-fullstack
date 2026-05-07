This is the moment where your code finally "comes to life." **Step 5: Instantiation** is the physical creation of the Java object in memory. If we were building a house, this is the day the foundation is poured and the frame goes up.

---

### Step 5: Instantiation (The `new` Moment)

In this phase, Spring iterates through the sorted list from Step 4 and starts calling constructors. However, it doesn't just call `new MyClass()`; it uses a strategy.

#### 1. The Strategy: `InstantiationStrategy`
Spring uses a specialized internal tool (usually `CglibSubclassingInstantiationStrategy`) to create the instance.
* **Constructor Injection:** If you used a constructor with arguments, Spring uses the sorted list to find the already-created dependencies and passes them in.
* **Factory Methods:** For your code specifically, Spring calls your `@Bean` methods. It literally invokes the `dataSource()` and `jdbcTemplate()` methods in your `AppConfig`.



#### 2. Reflection: The Secret Sauce
Under the hood, Spring uses **Java Reflection**. It looks at the `BeanDefinition`, finds the constructor, and tells the Java Virtual Machine (JVM): *"Create an object of this type right now."* #### 3. The "Naked Object" State
This is the most dangerous state for a bean. The object exists in memory (it has a memory address), but:
* Its `@Autowired` fields are still `null`.
* Its `@Value` properties haven't been set.
* It is **not** a "Spring Bean" yet; it is just a standard Java object sitting in a "pre-initialization" bucket.

---

### Key Distinctions in your Code

| Bean | How it is Instantiated |
| :--- | :--- |
| **`dataSource`** | Spring calls your `dataSource()` method. A `HikariDataSource` object is created. |
| **`jdbcTemplate`** | Spring calls your `jdbcTemplate(dataSource)` method, passing in the object created above. |

### Why Spring uses CGLIB for `@Configuration`
You might wonder: *"If I call `dataSource()` inside my config, won't it create a NEW object every time?"*
**No.** Because of **Step 5**, Spring wraps your `@Configuration` class in a **CGLIB Proxy**.
* When you call the method, the proxy intercepts it.
* It checks: *"Do I already have this bean?"* * If yes, it returns the existing one. This ensures your beans remain **Singletons**.

---

### Summary of State
At the end of Step 5:
* **The Java Object exists** (Memory is allocated).
* **The Constructor has run.**
* **The object is "Naked"** (No dependencies injected into fields yet).
