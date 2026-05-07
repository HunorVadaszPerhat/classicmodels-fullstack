*** 
## AOP is a BeanPostProcessor — AnnotationAwareAspectJAutoProxyCreator implements BeanPostProcessor and replaces beans with proxies in postProcessAfterInitialization
![img.png](img.png)
*** 
## BeanFactory: AOP broken — the AOP BPP is never auto-registered; @Transactional and @Aspect are silently ignored; the raw bean is returned unchanged
![img_1.png](img_1.png)
*** 
## ApplicationContext: AOP automatic — @EnableAspectJAutoProxy triggers auto-registration; every matching bean gets a CGLIB proxy at startup
![img_2.png](img_2.png)
*** 
## Declare an aspect — @Aspect @Component + @Around with a pointcut expression; OrderService is untouched, aspect is completely separate
![img_3.png](img_3.png)
*** 
## Exam contrast — the definitive side-by-side: BeanFactory = silent AOP failure, ApplicationContext = automatic proxying for @Transactional, @Aspect, @Cacheable
![img_4.png](img_4.png)
*** 