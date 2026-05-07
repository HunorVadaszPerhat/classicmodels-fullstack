***
## Practical payoffs of Inversion of Control (IoC): Enterprise integration — transactions, security, caching, etc. can be layered on without polluting business logic
***
## The pollution problem — manual txManager.begin(), security checks, and cache evictions crammed into every method
![img.png](img.png)
***
## AOP proxies — how Spring wraps beans at startup so callers hit the proxy, not your class directly
![img_1.png](img_1.png)
***
## @Transactional — the proxy handles begin / commit / rollback; your method has zero TX code
![img_2.png](img_2.png)
***
## @PreAuthorize — Spring Security's proxy evaluates SpEL before entry; no SecurityContextHolder calls
![img_3.png](img_3.png)
***
## @Cacheable / @CacheEvict — caching proxy checks the store on entry and stores/evicts on exit
![img_4.png](img_4.png)
***
## Full payoff — three annotations on one method; the class body is pure domain logic with zero infrastructure imports
![img_5.png](img_5.png)
***