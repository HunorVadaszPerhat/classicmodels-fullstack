package com.hunor.classicmodelsbackend.tx;

/**
 * <strong>Deprecated stub.</strong>
 *
 * <p>This class used to implement the JDK {@code InvocationHandler}
 * directly, with all the transaction logic inlined. It has been
 * superseded by {@link MyTransactionalAdvice} (which implements
 * {@link MyMethodInterceptor} so it can compose with other advices in
 * a chain) and {@link MyChainInvocationHandler} (the actual
 * {@code InvocationHandler} used by every proxy this package creates).</p>
 *
 * <p>The file is left in place as a deprecation pointer so anyone
 * grepping for "MyTransactionInterceptor" lands here and sees what
 * replaced it. Safe to delete entirely once no tooling references it.</p>
 *
 * @deprecated replaced by {@link MyTransactionalAdvice} and
 *             {@link MyChainInvocationHandler}.
 */
@Deprecated(forRemoval = true)
public final class MyTransactionInterceptor {

    private MyTransactionInterceptor() {
        // Not instantiable.
    }
}
