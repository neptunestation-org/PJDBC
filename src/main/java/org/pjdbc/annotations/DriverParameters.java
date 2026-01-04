package org.pjdbc.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for multiple {@link DriverParameter} annotations.
 *
 * <p>This annotation is automatically used by the compiler when multiple
 * {@code @DriverParameter} annotations are placed on a single class.
 * You typically don't need to use this annotation directly.</p>
 *
 * @see DriverParameter
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DriverParameters {

    /**
     * The array of driver parameter annotations.
     *
     * @return the parameter annotations
     */
    DriverParameter[] value();
}
