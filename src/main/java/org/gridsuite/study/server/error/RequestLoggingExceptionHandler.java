/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.StringJoiner;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
public class RequestLoggingExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger A_LOGGER = LoggerFactory.getLogger(RequestLoggingExceptionHandler.class);

    @Override
    protected ResponseEntity<@NonNull Object> handleTypeMismatch(
            @NonNull TypeMismatchException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch
                && request instanceof ServletWebRequest servletWebRequest) {
            HttpServletRequest servletRequest = servletWebRequest.getRequest();
            String fullUri = buildFullUri(servletRequest);
            A_LOGGER.error(
                "{} : method={} uri={} paramCausingIssue={} valueOfParamCausingIssue={}",
                mismatch.getMessage(),
                servletRequest.getMethod(),
                fullUri,
                mismatch.getName(),
                mismatch.getValue(),
                ex);
        }
        return super.handleTypeMismatch(ex, headers, status, request);
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
