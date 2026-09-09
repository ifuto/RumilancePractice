package org.junit.jupiter.api;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.METHOD,ElementType.TYPE}) public @interface AfterEach { String value() default ""; }
