package org.junit.jupiter.params.provider;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface EnumSource {
    Class<? extends Enum<?>> value() default NullEnum.class;
    String[] names() default {};
    enum NullEnum { }
}
