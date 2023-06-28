package io.github.mattidragon.configloader.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When config toolkit is applied as an annotation processor, this annotation causes a mutable version of the record it's applied on to generate.
 * The annotation processor places the mutable class in the same package, with the same name, but prefixed with {@code Mutable}.
 * If any component of the target record is also annotated with this, then the mutable class will use the mutable version of that record.
 * <p>
 * Can only be applied to records, other classes will cause the annotation processor to emit an error.
 * Records that this annotation is used on must also implement the {@code Source} interface found in the mutable version of the class.
 * This interface allows the annotation processor to indirectly inject methods into the target record.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface GenerateMutable {
    /**
     * If {@code true} (default) the generated mutable class will have getters and setter for its fields and the fields will be private.
     * <br>
     * If {@code false} the generated mutable class will have public fields and no accessors
     */
    boolean encapsulateFields() default true;

    boolean useFancyMethodNames() default false;
}
