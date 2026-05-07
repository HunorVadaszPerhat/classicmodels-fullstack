***
## Dependency Injection (DI) — the container pushes dependencies into your class. Your class is passive; it just receives what it needs
***
## Active class — arrows point outward from ReportService as it reaches out with new to grab its own deps
![img.png](img.png)
***
## Passive class — arrows flip inward; the class just declares a constructor and receives what arrives
![img_1.png](img_1.png)
***
## Spring as the pusher — the container reads annotations, builds beans in order, and calls your constructor
![img_2.png](img_2.png)
***
## Three injection styles — constructor (recommended), setter (optional deps), field (avoid) side-by-side
![img_3.png](img_3.png)
***
## Test as the mini-container — you pass mocks directly to the constructor; Spring never starts
![img_4.png](img_4.png)
***
## Full picture — the same constructor works for prod (Spring pushes real beans) and tests (you push mocks)
![img_5.png](img_5.png)