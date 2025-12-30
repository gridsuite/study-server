/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.aspect;

import org.springframework.core.MethodParameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * @author Kevin Le Saulnier <kevin.le-saulnier at rte-france.com>
 */
public final class AnnotatedParameterExtractor {
    private AnnotatedParameterExtractor() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    // extract annotated parameter with checked type - if not found, found multiple times, or found with wrong type, this throw an exception
    public static <T> T extractRequiredParameter(
        Method method,
        Object[] args,
        Class<? extends Annotation> annotation,
        Class<T> expectedType
    ) {
        T result = null;

        for (int i = 0; i < args.length; i++) {
            MethodParameter param = new MethodParameter(method, i);

            if (param.hasParameterAnnotation(annotation)) {
                if (result != null) {
                    throw new IllegalStateException(
                        "Multiple parameters annotated with @"
                            + annotation.getSimpleName()
                    );
                }

                Object value = args[i];
                if (!expectedType.isInstance(value)) {
                    throw new IllegalStateException(
                        "Parameter annotated with @"
                            + annotation.getSimpleName()
                            + " must be of type "
                            + expectedType.getSimpleName()
                    );
                }

                result = expectedType.cast(value);
            }
        }

        if (result == null) {
            throw new IllegalStateException(
                "Missing parameter annotated with @"
                    + annotation.getSimpleName()
            );
        }

        return result;
    }
}
