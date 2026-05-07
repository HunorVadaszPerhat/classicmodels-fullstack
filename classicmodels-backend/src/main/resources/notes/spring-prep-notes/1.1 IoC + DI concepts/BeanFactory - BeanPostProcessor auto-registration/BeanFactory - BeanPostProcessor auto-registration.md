*** 
## What BPP does — the two hook methods (Before/AfterInitialization), their position relative to @PostConstruct, and how returning a different object replaces the bean (how AOP works)
![img.png](img.png)
*** 
## BeanFactory: manual — addBeanPostProcessor() must be called explicitly; forgetting it silently breaks @Autowired, @Value, and AOP
![img_1.png](img_1.png)
*** 
## ApplicationContext: automatic — just declare @Component implementing BeanPostProcessor; the context detects and registers it before all other beans
![img_2.png](img_2.png)
*** 
## Built-in BPPs — AutowiredAnnotationBeanPostProcessor, CommonAnnotationBeanPostProcessor, AnnotationAwareAspectJAutoProxyCreator — all auto-registered by ApplicationContext, none by BeanFactory
![img_3.png](img_3.png)
*** 
## Lifecycle position — BPPs are instantiated in Phase 1 before any regular singleton, guaranteeing they're ready to intercept all bean creations
![img_4.png](img_4.png)
*** 
## Exam contrast — coral BeanFactory (manual, fragile) vs teal ApplicationContext (automatic), the exact framing the exam uses
![img_5.png](img_5.png)
*** 