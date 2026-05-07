***
## The lazy problem — BeanFactory defers creation; a broken bean hides until first use, possibly in production
![img.png](img.png)
***
## Eager init payoff — ApplicationContext pre-instantiates all singletons at startup; if you reach the next line, all beans are healthy
![img_1.png](img_1.png)
***
## What gets eagerly initialised — only non-lazy singletons; @Scope("prototype") and @Lazy still defer
![img_2.png](img_2.png)
***
## Three errors caught at startup — NoSuchBeanDefinitionException, NoUniqueBeanDefinitionException, and BeanCurrentlyInCreationException (circular ref) — all surface before the first request
![img_3.png](img_3.png)
***
## The exam rule — the side-by-side contrast the 2V0-72.22 expects: BeanFactory = lazy, ApplicationContext = eager, same DI — different timing
![img_4.png](img_4.png)
***
