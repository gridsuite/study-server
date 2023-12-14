package org.gridsuite.study.server.utils.annotations.validation;

import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * The annotated element must be either:<li>
 *     <ul>null</ul>
 *     <ul>not be null and must contain at least one non-whitespace character.</ul>
 * </li>
 * Accepts CharSequence.
 * @see jakarta.validation.constraints.Null
 * @see jakarta.validation.constraints.NotBlank
 */
@Pattern(regexp = "^(?!\\s*$).+", message = "must not be blank") //regexp not matched if null
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NullOrNotBlank {
}
