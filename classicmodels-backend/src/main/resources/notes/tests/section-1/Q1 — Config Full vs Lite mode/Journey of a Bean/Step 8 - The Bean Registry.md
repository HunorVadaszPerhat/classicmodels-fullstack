Step 8 is the moment the "Construction Site" tape is finally removed. Your bean has survived the constructor, the injections, and the initialization "finishing school." Now, it is officially a part of the application’s working staff.

---

### Step 8: The Bean Registry (Ready)

In this phase, the bean is moved from the "in-progress" area to the **Singleton Objects Cache**.

#### 1. The Singleton Cache (The "First-Level" Cache)
Spring maintains a specialized internal map called the `singletonObjects` (usually a `ConcurrentHashMap`).
* **The Handover:** The fully polished, initialized, and potentially proxied object is placed here.
* **The Key:** The name of your bean (e.g., `"jdbcTemplate"`).
* **The Value:** The reference to the ready-to-use object.



#### 2. Serving Requests
Now that the bean is in the registry, it is available for **on-demand lookup**.
* If a piece of your code calls `applicationContext.getBean("jdbcTemplate")`, Spring doesn't run any of the previous steps again. It simply looks at the map and hands over the existing reference.
* This is why Spring beans are "Singletons" by default—everyone is sharing the exact same instance stored in this registry.

#### 3. Availability for Late-Bloomers
If there are any "Lazy" beans (beans marked with `@Lazy`) that haven't been created yet, they can now safely find and use your `jdbcTemplate` the moment they are eventually initialized.

---

### Step 9: Application Ready (The Final Whistle)

Once **every** non-lazy singleton bean has reached Step 8, the `ApplicationContext` is officially "Refreshed."

1.  **Event Broadcast:** Spring publishes the `ContextRefreshedEvent`.
2.  **Runners Execute:** If you have any classes implementing `CommandLineRunner` or `ApplicationRunner`, they run **now**. This is your first chance to actually use the beans to do work (like seeding a database).
3.  **The Web Server Starts:** If it's a web app, the embedded Tomcat/Netty server starts listening for traffic.



---

### Summary of the "Ready" State

| Feature | Status |
| :--- | :--- |
| **Location** | `DefaultSingletonBeanRegistry` (The Cache). |
| **Integrity** | 100% Configured, Proxied, and Validated. |
| **Accessibility** | Available for `@Autowired` and `getBean()`. |
| **Lifespan** | This is the state the bean stays in until the app is shut down. |

### The "Steady State"
Your `dataSource` and `jdbcTemplate` are now sitting in memory, waiting for a user to hit a REST endpoint so they can jump into action. They will stay exactly like this until the application receives a "Stop" signal (like you hitting the red button in your IDE).

---
