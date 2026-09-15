package com.ing.ide.main.testar.mcp.helper;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

public final class McpNames {

    private McpNames() {}

    @FunctionalInterface
    public interface MethodRef<T> extends Serializable {
        void call(T target); // never actually called; we just capture the reference
    }

    public static <T> String of(MethodRef<T> ref) {
        try {
            Method m = ref.getClass().getDeclaredMethod("writeReplace");
            m.setAccessible(true);
            SerializedLambda sl = (SerializedLambda) m.invoke(ref);
            return sl.getImplMethodName();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot resolve method name", e);
        }
    }
}
