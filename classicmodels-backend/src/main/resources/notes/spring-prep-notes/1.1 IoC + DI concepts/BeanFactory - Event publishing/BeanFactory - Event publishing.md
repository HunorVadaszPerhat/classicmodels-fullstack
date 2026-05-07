***
## BeanFactory has no events — the interface hierarchy shows ApplicationEventPublisher is only on ApplicationContext, not BeanFactory
![img.png](img.png)
***
## Define an event — plain POJO (Spring 4.2+) vs classic ApplicationEvent extension; both shown
![img_1.png](img_1.png)
***
## Publish with publishEvent() — inject ApplicationEventPublisher, call it after the business action; context fans out synchronously
![img_2.png](img_2.png)
***
## Listen with @EventListener — method parameter type is the filter; multiple listeners receive the same event; publisher imports neither
![img_3.png](img_3.png)
***
## Built-in lifecycle events — ContextRefreshedEvent, ContextClosedEvent, etc.; the exam answer is "BeanFactory fires none of these"
![img_4.png](img_4.png)
***
## Full picture — publisher → event POJO → context → multiple @EventListener methods; the key insight is zero coupling between publisher and listeners
![img_5.png](img_5.png)
***