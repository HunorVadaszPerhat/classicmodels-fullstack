***
## BeanFactory interface — the root contract; getBean() is there but DI still happens internally when beans are created
![img.png](img.png)
***
## How DI works inside the factory — factory resolves PaymentGateway, then calls new OrderService(gateway); your class stays passive
![img_1.png](img_1.png)
***
## Lazy vs eager — the key exam distinction: BeanFactory creates on first request; ApplicationContext creates all singletons at startup (catches wiring errors early)
![img_2.png](img_2.png)
***
## ApplicationContext extends BeanFactory — the interface hierarchy plus the two concrete implementations you'll see on the exam (AnnotationConfigApplicationContext, ClassPathXmlApplicationContext)
![img_3.png](img_3.png)
***
## Full startup sequence — scan → graph → instantiate → inject → @PostConstruct; the five-step mental model the exam expects
![img_4.png](img_4.png)
***