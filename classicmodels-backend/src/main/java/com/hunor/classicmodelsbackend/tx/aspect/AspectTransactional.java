package com.hunor.classicmodelsbackend.tx.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Spring-AOP equivalent of {@code com.hunor.classicmodelsbackend.tx.MyTransactional}.
 *
 * <p>Identical role: marker annotation that pins a method as
 * "transactional." The behaviour comes from
 * {@link AspectTransactionalAspect}, an {@code @Aspect} bean that
 * Spring AOP wires up automatically when the {@code spring-boot-starter-aop}
 * is on the classpath.</p>
 *
 * <p>Side-by-side comparison with {@link com.hunor.classicmodelsbackend.tx.MyTransactional}:</p>
 *
 * <table>
 *   <caption>Hand-rolled vs Spring-AOP</caption>
 *   <tr><th>Concern</th>
 *       <th>Hand-rolled (@MyTransactional)</th>
 *       <th>Spring AOP (@AspectTransactional)</th></tr>
 *   <tr><td>Annotation file</td><td>~10 lines</td><td>~10 lines</td></tr>
 *   <tr><td>Advice / interceptor</td><td>MyTransactionalAdvice (~60 lines)</td>
 *       <td>AspectTransactionalAspect (~50 lines)</td></tr>
 *   <tr><td>Bean post-processor</td><td>MyTransactionalBeanPostProcessor (~80 lines, written by us)</td>
 *       <td>None — Spring's framework provides it</td></tr>
 *   <tr><td>Chain handler</td><td>MyChainInvocationHandler (~60 lines)</td>
 *       <td>None — Spring's framework provides it</td></tr>
 *   <tr><td>Total LoC we maintain</td><td>~250</td><td>~60</td></tr>
 *   <tr><td>What we learn</td><td>Everything about JDK proxies and reflection</td>
 *       <td>How to declare advice via pointcut expressions</td></tr>
 * </table>
 *
 * <p>That's the trade-off in numbers. The framework version is
 * dramatically shorter because Spring is doing the post-processor +
 * proxy + chain work behind the scenes.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AspectTransactional {
}
