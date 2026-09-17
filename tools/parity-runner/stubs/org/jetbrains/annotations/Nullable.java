package org.jetbrains.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** コンパイル用の最小スタブ (サンドボックスに annotations jar が無いため)。実行時影響なし。 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE,
        ElementType.TYPE_USE})
public @interface Nullable {
    String value() default "";
}
