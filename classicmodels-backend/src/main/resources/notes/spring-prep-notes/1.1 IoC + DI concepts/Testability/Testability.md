***
## The problem — new StripeGateway() inside the service means every test hits the real network (shown blocked in the diagram)
![img.png](img.png)
***
## Extract the seam — PaymentGateway interface splits prod and test impls cleanly
![img_1.png](img_1.png)
*** 
## Constructor injection — PaymentService accepts any PaymentGateway; zero concrete imports
![img_2.png](img_2.png)
*** 
## Plain Mockito unit test — no Spring context at all; the mock is passed directly to the constructor
![img_3.png](img_3.png)
***
## @MockBean integration test — Spring boots but evicts StripeGateway, replacing it with a Mockito proxy
![img_4.png](img_4.png)
*** 
## Full payoff — all three tiers (unit / integration / production) use the identical PaymentService class
![img_6.png](img_6.png)
***