***
## The problem — lazy BeanFactory hides broken beans until first use; error surfaces at runtime in production
![img.png](img.png)
***
## Eager init — ApplicationContext.refresh() pre-instantiates all singletons; if you pass that line, all beans are healthy
![img_1.png](img_1.png)
***
## What is and isn't eager — singleton (eager), @Scope("prototype") (lazy per-request), @Lazy singleton (deferred by choice)
![img_2.png](img_2.png)
***
## Three errors caught at startup — NoSuchBeanDefinitionException, NoUniqueBeanDefinitionException, BeanCurrentlyInCreationException — all before request 1
![img_3.png](img_3.png)
***
## @Lazy opt-out — defer one heavy bean while keeping all others eagerly validated; tradeoff shown clearly
![img_4.png](img_4.png)
***
## Full picture — the Spring Boot startup sequence: scan → register BPPs → pre-instantiate → ready; wiring errors thrown in step 3
![img_5.png](img_5.png)
***