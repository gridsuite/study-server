package org.gridsuite.study.server.aspect;

import org.springframework.core.MethodParameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class AnnotatedParameterExtractor { //TODO: improve throws
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
