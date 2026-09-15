package com.ing.ide.main.testar.mcp.helper;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface McpParam {
    String name();

    String description();

    boolean required() default true;
}
