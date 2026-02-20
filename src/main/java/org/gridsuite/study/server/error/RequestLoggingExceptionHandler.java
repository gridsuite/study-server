/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.StringJoiner;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
/*
    This handler is used to catch MethodArgumentTypeMismatchException exceptions
    that do not provide enough information on their own and to log details about
    the request that caused them.

    After logging is completed, the exception propagates normally, so this class
    does not modify any behavior.
*/
public class RequestLoggingExceptionHandler implements HandlerExceptionResolver {
    private static final Logger A_LOGGER = LoggerFactory.getLogger(RequestLoggingExceptionHandler.class);

    @Override
    public @Nullable ModelAndView resolveException(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            Object handler,
            @NonNull Exception ex) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            String fullUri = buildFullUri(request);
            A_LOGGER.error(
                "{} : method={} uri={} paramCausingIssue={} valueOfParamCausingIssue={}",
                mismatch.getMessage(),
                request.getMethod(),
                fullUri,
                mismatch.getName(),
                mismatch.getValue());
        }
        return null;
    }

    private static String buildFullUri(HttpServletRequest request) {
        String query = buildQueryString(request.getParameterMap());
        return request.getRequestURI() + (query.isEmpty() ? "" : "?" + query);
    }

    private static String buildQueryString(Map<String, String[]> parameters) {
        StringJoiner joiner = new StringJoiner("&");
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            //If the param doesn't have a value we can go on directly
            if (values == null || values.length == 0) {
                joiner.add(key);
                continue;
            }
            for (String value : values) {
                joiner.add(key + "=" + value);
            }
        }
        return joiner.toString();
    }
}
