*** 
## Plain object vs bean — the single defining difference: container manages the lifecycle vs you manage it; code shows the new call removed
![img.png](img.png)
*** 
## Three declaration styles — @Component/stereotypes, @Bean in @Configuration, and XML; all three produce the same result
![img_1.png](img_1.png)
*** 
## BeanDefinition — the metadata blueprint Spring stores before creating the instance; class, scope, lazyInit, init/destroy methods
![img_2.png](img_2.png)
*** 
## Scope — singleton (one shared instance, a == b) vs prototype (new per request, a != b); singleton is the default
![img_3.png](img_3.png)
*** 
## Lifecycle hooks — constructor inject → @PostConstruct → ready → @PreDestroy; prototype beans skip @PreDestroy
![img_4.png](img_4.png)
*** 
## Full journey — declare → BeanDefinition → instantiate+DI+BPP hooks → singleton cache → destroy on ctx.close()
![img_5.png](img_5.png)