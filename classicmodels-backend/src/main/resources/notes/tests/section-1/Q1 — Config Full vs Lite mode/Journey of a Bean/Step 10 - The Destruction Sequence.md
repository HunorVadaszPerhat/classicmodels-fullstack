All good things must come to an end. After your application has served thousands of requests, it eventually needs to shut down. Whether you hit "Stop" in your IDE or the server receives a `SIGTERM`, Spring ensures your beans don't just "vanish"—they get a proper retirement.

---

### Phase 7 & 8: The Destruction Sequence

When the `ApplicationContext.close()` method is called, Spring begins the process of dismantling the Bean Registry.

#### 1. The Trigger: Closing the Context
Spring emits a `ContextClosedEvent`. This is the signal for all beans to start "packing their bags." Before the objects are wiped from memory, Spring gives them a chance to clean up resources like open database connections, file handles, or network sockets.



#### 2. The Cleanup Callbacks
Just like the initialization phase, there is a specific order for cleaning up:
* **`@PreDestroy`**: The first signal. This is the preferred way to handle cleanup (e.g., stopping a background thread).
* **`DisposableBean.destroy()`**: If your bean (like `HikariDataSource`) implements this interface, this method is called next.
* **`destroyMethod`**: If you defined `@Bean(destroyMethod = "close")` in your configuration, this is the final custom step executed.

#### 3. The "Reverse Order" Rule
This is the most critical part of destruction management. Spring destroys beans in the **exact reverse order** of their creation.
* **Why?** Because `JdbcTemplate` depends on `DataSource`. If Spring destroyed the `DataSource` first, the `JdbcTemplate` might crash if it tried to run one last cleanup query.
* **The Result:** Spring destroys `JdbcTemplate` first, then safely shuts down the `DataSource`.



#### 4. Removal from Registry & GC
Once the cleanup methods finish, Spring removes the reference from its internal `singletonObjects` map.
* The bean is now just a regular Java object with no one pointing to it.
* **Step 15 (Garbage Collection):** The JVM’s Garbage Collector sees that the object is no longer used and reclaims the memory. The bean is officially gone.

---

### The Grand Finale: The Full Journey Summary

You’ve now traced the life of your `DataSource` and `JdbcTemplate` from a simple `@Bean` annotation to its final deletion from memory.

| Phase | Core Goal |
| :--- | :--- |
| **Preparation** | Scanning and creating the "Recipe" (`BeanDefinition`). |
| **Birth** | Calling `new` and injecting dependencies. |
| **Finishing School** | Running Aware interfaces, `@PostConstruct`, and Proxies. |
| **Active Duty** | Sitting in the Singleton Registry serving the app. |
| **Retirement** | Running `@PreDestroy` and closing connections. |
| **Gone** | Garbage collection reclaims the memory. |

### Final Thought
The reason we use Spring is so we **don't** have to manage this complex 15-step dance ourselves. By simply writing `@Bean`, you are delegating all this orchestration to the container, ensuring your app is robust, memory-efficient, and properly connected.

How does it feel seeing the "magic" of Spring broken down into these mechanical steps? Does it make debugging your code feel a bit more approachable?