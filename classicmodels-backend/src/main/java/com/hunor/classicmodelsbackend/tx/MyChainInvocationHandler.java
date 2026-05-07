package com.hunor.classicmodelsbackend.tx;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * The single {@link InvocationHandler} that backs every proxy created by
 * {@link MyAroundBeanPostProcessor}.
 *
 * <p>Built once per bean. Holds a precomputed map of "interface method →
 * list of advices to run for that method." When the JDK proxy receives
 * a call, this handler:</p>
 * <ol>
 *   <li>resolves the corresponding implementation method on the target
 *       (so we can read annotations and call concrete behaviour),</li>
 *   <li>looks up the advice chain for that method,</li>
 *   <li>if no chain → passes through to the target directly,</li>
 *   <li>if a chain exists → constructs a {@link MyMethodInvocation}
 *       and kicks off the chain via {@code proceed()}.</li>
 * </ol>
 *
 * <p>By precomputing the chain at proxy creation time (rather than
 * scanning annotations on every call) we avoid reflection in the hot
 * path. Spring's AOP infrastructure does the same thing — it caches
 * the advisor list per method.</p>
 */
public class MyChainInvocationHandler implements InvocationHandler {

    private final Object target;

    /**
     * Precomputed map: implementation method → ordered advice chain.
     * Methods absent from this map have no advice and are forwarded
     * directly to the target.
     */
    private final Map<Method, List<MyMethodInterceptor>> chainsByImplMethod;

    public MyChainInvocationHandler(Object target,
                                    Map<Method, List<MyMethodInterceptor>> chainsByImplMethod) {
        this.target = target;
        this.chainsByImplMethod = chainsByImplMethod;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // The annotation lives on the implementation, not on the
        // interface method we received. Resolve once.
        Method implMethod = resolveImplMethod(method);

        List<MyMethodInterceptor> chain = (implMethod == null)
                ? null
                : chainsByImplMethod.get(implMethod);

        if (chain == null || chain.isEmpty()) {
            // No advice registered → straight delegation.
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        // Run the chain. The MyMethodInvocation walks through every
        // advice in turn and ends by calling the target via reflection.
        return new MyMethodInvocation(target, implMethod, args, chain).proceed();
    }

    /**
     * Map an interface method (received by the proxy) to the matching
     * concrete method on the target's class, where annotations live.
     * Returns null when there's no match — shouldn't happen in practice
     * because the proxy is built from the target's interfaces.
     */
    private Method resolveImplMethod(Method interfaceMethod) {
        try {
            return target.getClass().getMethod(
                    interfaceMethod.getName(),
                    interfaceMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
