
---

### Phase 1: Creation

1.  **Instantiation — constructor called**: The first step. The Spring container creates the bean instance by calling its constructor.
2.  **Dependency injection — @Autowired, setters, fields**: Spring populates the bean's dependencies and properties using annotations like `@Autowired`, setter methods, or direct field injection.

### Phase 2: Aware

*(These steps occur if the bean implements certain `Aware` interfaces)*

3.  **BeanNameAware setBeanName()**: If implemented, the bean's unique ID/name is provided to it.
4.  **BeanFactoryAware setBeanFactory()**: If implemented, the bean receives a reference to the Bean Factory that created it.
5.  **ApplicationContextAware setApplicationContext()**: If implemented, the bean gets access to the full Spring application context.

### Phase 3: Post-Process (Pre-Initialization)

6.  **BeanPostProcessor — postProcessBeforeInitialization()**: This method from the `BeanPostProcessor` interface is called for *ALL* beans in the context. It's often used for things like setting up AOP proxies, data validation, or other custom processing that must happen before init callbacks.

### Phase 4: Init

*(Custom initialization methods are called here)*

7.  **@PostConstruct JSR-250, preferred**: If the method is annotated with `@PostConstruct`, it is called. This is the modern, preferred method.
8.  **InitializingBean afterPropertiesSet()**: If the bean implements the `InitializingBean` interface, its `afterPropertiesSet()` method is called.
9.  **@Bean(initMethod) / init-method XML**: A custom, named initialization method declared on the `@Bean` definition (or in XML) is executed.

### Phase 5: Post-Process (Post-Initialization)

10. **BeanPostProcessor — postProcessAfterInitialization()**: This final `BeanPostProcessor` hook is called. It can wrap the fully initialized bean in a proxy if needed (for features like `@Transactional` or `@Async`).

### Phase 6: Ready

11. **Bean is fully initialised — serving requests**: The bean's initialization is complete. It is now ready to be used by other beans or for handling application requests.

### Phase 7: Ready (Prior to Destruction)

*(Shutdown sequence begins; custom cleanup methods are preparing to run)*

12. **@PreDestroy JSR-250, preferred**: If a method is annotated with `@PreDestroy`, it is called immediately before the bean is destroyed. This is the preferred way for defining cleanup logic.
13. **DisposableBean destroy()**: If the bean implements the `DisposableBean` interface, its `destroy()` method is executed.
14. **@Bean(destroyMethod) / destroy-method XML**: A custom, named destruction/cleanup method declared on the `@Bean` definition (or in XML) is executed.

### Phase 8: Destroy

15. **Garbage collected — bean removed from memory**: The final step. The bean instance is removed from the Spring container's context and is available for garbage collection.

---

### Legend
The diagram uses colors to group similar concepts:

* **Aware interfaces** (Steps 3, 4, 5)
* **BeanPostProcessor** hooks (Steps 6, 10)
* **Init callbacks** (Steps 7, 8, 9)
* **Destroy callbacks** (Steps 12, 13, 14)