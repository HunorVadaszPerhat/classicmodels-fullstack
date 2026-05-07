***
## Practical payoffs of Inversion of Control (IoC): Decoupling — OrderService depends on an interface, not a concrete class.Swap the implementation without touching OrderService
***
## The coupling problem — OrderService hardwires EmailNotifier with new, making SmsNotifier unreachable (shown dimmed in the diagram)
![img.png](img.png)
***
## Extract the interface — Notifier contract is born; both impls become interchangeable
![img_1.png](img_1.png)
***
## Service depends on interface — OrderService imports only Notifier, never the concrete classes
![img_2.png](img_2.png)
*** 
## Swap with zero edits — @Profile("sms") + one properties line flips the whole app
![img_5.png](img_5.png)
*** 
## Full payoff — @TestConfiguration swaps the entire wiring for tests; OrderService is byte-for-byte identical in both configs
![img_6.png](img_6.png)
***