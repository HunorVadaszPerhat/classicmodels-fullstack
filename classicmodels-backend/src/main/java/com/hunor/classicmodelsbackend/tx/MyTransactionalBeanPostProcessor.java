package com.hunor.classicmodelsbackend.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean post-processor that wraps any bean carrying around-style
 * annotations ({@link MyTransactional}, {@link MyTimed}, future
 * additions) in a JDK dynamic proxy backed by
 * {@link MyChainInvocationHandler}.
 *
 * <p>For every public method on the bean's class, we look at which
 * advice annotations are present and build an ordered chain of
 * {@link MyMethodInterceptor}s. Methods with no advice are not added to
 * the map — the chain handler short-circuits to direct delegation
 * for those, so non-annotated methods stay zero-overhead.</p>
 *
 * <h3>Chain ordering</h3>
 *
 * <p>The chain is built so that <em>outer</em> advice comes first.
 * Concretely: timing wraps the transaction (so logged elapsed times
 * include the commit), not the other way round. This matches the
 * intuition "what does the caller experience?" — the caller experiences
 * the transaction's commit/rollback as part of the call.</p>
 *
 * <p>The class is still named {@code MyTransactionalBeanPostProcessor}
 * for git-history continuity. A more accurate name would be
 * {@code MyAroundBeanPostProcessor}; the responsibility expanded as we
 * added more annotations. Renames are cheap if you ever fancy doing it.</p>
 *
 * <h3>Limitations</h3>
 * <p>JDK dynamic proxies require the proxied class to implement at least
 * one interface. Beans with around-style annotations but no interface
 * are logged as warnings and left un-proxied; their annotations are
 * silently inert.</p>
 */
@Component
@Slf4j
public class MyTransactionalBeanPostProcessor implements BeanPostProcessor {

    private final DataSource dataSource;

    public MyTransactionalBeanPostProcessor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();

        // Skip our own infrastructure classes.
        if (beanClass.getPackageName().startsWith("com.hunor.classicmodelsbackend.tx")) {
            return bean;
        }

        // Build the per-method chains. If no method has any advice, we
        // return the bean unchanged — no proxy, no overhead.
        Map<Method, List<MyMethodInterceptor>> chains = buildChains(beanClass);
        if (chains.isEmpty()) {
            return bean;
        }

        // JDK proxies need at least one interface.
        Class<?>[] interfaces = beanClass.getInterfaces();
        if (interfaces.length == 0) {
            log.warn("Bean '{}' ({}) has around-style annotations but " +
                    "implements no interfaces. JDK dynamic proxies need " +
                    "an interface; no proxy will be created.",
                    beanName, beanClass.getName());
            return bean;
        }

        log.info("Wrapping bean '{}' ({}) with chain proxy. {} method(s) advised.",
                beanName, beanClass.getSimpleName(), chains.size());

        return Proxy.newProxyInstance(
                beanClass.getClassLoader(),
                interfaces,
                new MyChainInvocationHandler(bean, chains));
    }

    /**
     * For each public method on {@code beanClass}, build the ordered
     * advice chain. Methods with zero advice are omitted from the map
     * entirely so the chain handler can short-circuit them.
     *
     * <p>Outer advice goes first in the list. We add timing first so
     * it wraps the transaction; if you ever add e.g. {@code @MyRetry}
     * the natural place is between timing and transaction (retry
     * outside the tx so each attempt gets its own).</p>
     */
    private Map<Method, List<MyMethodInterceptor>> buildChains(Class<?> beanClass) {
        Map<Method, List<MyMethodInterceptor>> result = new HashMap<>();

        for (Method method : beanClass.getMethods()) {
            List<MyMethodInterceptor> chain = new ArrayList<>(2);

            // ORDER: outermost advice first.
            // 1. Timing — wraps everything, including the transaction's commit.
            if (method.isAnnotationPresent(MyTimed.class)) {
                chain.add(new MyTimedAdvice());
            }
            // 2. Transactional — opens the JDBC tx, runs the rest of the chain inside.
            if (method.isAnnotationPresent(MyTransactional.class)) {
                chain.add(new MyTransactionalAdvice(dataSource));
            }

            if (!chain.isEmpty()) {
                result.put(method, chain);
            }
        }
        return result;
    }
}
