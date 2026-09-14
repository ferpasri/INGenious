package com.ing.ide.main.testar.mcp.helper;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpMethod {
    String name() default "";

    String description();
}
