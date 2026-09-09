package org.junit.jupiter.params.provider;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface ValueSource {
    int[] ints() default {}; long[] longs() default {}; double[] doubles() default {};
    String[] strings() default {}; Class<?>[] classes() default {}; boolean[] booleans() default {};
}
