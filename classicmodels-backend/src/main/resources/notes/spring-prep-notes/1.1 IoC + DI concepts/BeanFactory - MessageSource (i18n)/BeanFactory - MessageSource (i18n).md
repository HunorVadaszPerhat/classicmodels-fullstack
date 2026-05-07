***
## BeanFactory has no i18n — the interface hierarchy: MessageSource is on ApplicationContext, not BeanFactory
![img.png](img.png)
***
## MessageSource interface — the three key overloads; the exam focuses on getMessage(code, args[], locale)
![img_1.png](img_1.png)
***
## Properties files — messages.properties as default, messages_fr.properties as override; locale fall-through chain
![img_2.png](img_2.png)
***
## Register the bean — the bean name must be exactly "messageSource"; ApplicationContext auto-detects it by name
![img_3.png](img_3.png)
***
## Inject and call — inject MessageSource via constructor, call getMessage() with a live Locale
![img_4.png](img_4.png)
***
## Full picture — the complete flow inside an ApplicationContext boundary, with the exam-critical footer: "BeanFactory: no MessageSource support"
![img_5.png](img_5.png)
***