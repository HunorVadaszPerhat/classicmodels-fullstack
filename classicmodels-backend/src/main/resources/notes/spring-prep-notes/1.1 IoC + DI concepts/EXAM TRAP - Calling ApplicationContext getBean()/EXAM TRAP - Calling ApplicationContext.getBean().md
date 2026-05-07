***
## Spot the trap — getBean() inside @Service with orange highlight so the anti-pattern is visually unmissable
![img_5.png](img_5.png)
***
## Arrow direction rule — the single fastest mental test: out = Lookup, in = Injection
![img_6.png](img_6.png)
***
## Four disguises — getBean(type), getBean(name), ApplicationContextAware stored ref, static holder — all coral/red; one teal DI box for contrast
![img_7.png](img_7.png)
***
## The @Autowired distractor — field injection looks "different" but is still DI; this is the most common wrong answer on the exam
![img_8.png](img_8.png)
***
## Exam decision tree — a literal 5-second flowchart: scan for getBean() → Lookup; see constructor/setter param → Injection
![img_9.png](img_9.png)
***

