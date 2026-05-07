**Step 9: Application Ready** is the "Grand Opening" ceremony. Up until this point, the application was a construction site. Now, the ribbon is cut, the "Open" sign is flipped, and the application begins its actual job.

While Step 8 was about individual beans being ready, Step 9 is about the **entire ecosystem** being operational.

---

### 1. The `ApplicationReadyEvent`
Once the `ApplicationContext` is fully refreshed and all singleton beans are sitting in the registry, Spring Boot publishes the **`ApplicationReadyEvent`**.
* Any bean in your system can listen for this event using `@EventListener`.
* This is perfect for beans that need to perform an action only when they are 100% sure the *entire* app is stable (e.g., starting a background processing task).



---

### 2. Execution of "Runners"
This is a unique part of Spring Boot. Before the app starts taking external traffic, it looks for specific beans that implement these two interfaces:
* **`CommandLineRunner`**: Runs a `run(String... args)` method.
* **`ApplicationRunner`**: Runs a `run(ApplicationArguments args)` method.

If you have these beans, Spring executes them **now**. They are commonly used for:
* Seeding a database with initial data.
* Checking if an external file system is accessible.
* Printing a "Success" message to the console.

| Feature | `CommandLineRunner` | `ApplicationRunner` |
| :--- | :--- | :--- |
| **Input Type** | Raw String array (`String[]`). | Sophisticated `ApplicationArguments` object. |
| **Use Case** | Simple scripts/commands. | Complex argument parsing (e.g., `--debug --port=8080`). |
| **Order** | Can be ordered via `@Order`. | Can be ordered via `@Order`. |



---

### 3. Starting the Embedded Web Server
If you are building a web application (like a REST API), Spring Boot doesn't start the web server (Tomcat, Netty, or Undertow) until the very end.
* It waits until all beans are ready so that the first user who hits an endpoint doesn't get a `500 Internal Server Error` because a service was still initializing.
* The "Started Application in X seconds" log message only appears **after** the web server has successfully bound to its port (usually `8080`).

---

### 4. Transition to "Steady State"
At this point, the main thread that started the application usually finishes its startup work. The application enters **Steady State**:
* **The CPU settles down** as the heavy lifting of bean creation is over.
* **Memory usage stabilizes**, though it might dip slightly as the "startup-only" objects (like the scanner and temporary metadata) are garbage collected.
* **Health Indicators** (like `/actuator/health`) flip from `STARTING` to `UP`.

---

### Summary Checklist: What is "Ready"?

* [x] **Registry:** Every singleton bean is instantiated and cached.
* [x] **Proxies:** Every `@Transactional` or `@Async` bean is wrapped in its protective armor.
* [x] **Runners:** Any initial setup code has finished executing.
* [x] **Ports:** The web server is listening for incoming requests.
* [x] **Events:** The `ApplicationReadyEvent` has been fired.

### Why this is a "Bean" focus
Step 9 is the first time your beans interact with the **outside world**. Before Step 9, beans only "talked" to each other inside the container. After Step 9, they start talking to databases, other APIs, and your users.

**Congratulations!** You’ve successfully navigated the entire lifecycle from a piece of code in `AppConfig` to a live, production-ready application.

Do you have any specific scenarios or "what-if" questions about any of these steps?