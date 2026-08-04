/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

@Service
public class TransparentProxyService {
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
        "host",
        "content-length"
    );

    private final RemoteServicesProperties remoteServicesProperties;
    private final RestTemplate restTemplate;

    public TransparentProxyService(RemoteServicesProperties remoteServicesProperties,
                                   RestTemplate restTemplate) {
        this.remoteServicesProperties = remoteServicesProperties;
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<byte[]> forward(String serviceName,
                                           HttpServletRequest request,
                                           byte[] body) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        URI targetUri = buildTargetUri(serviceName, request);
        HttpEntity<byte[]> entity = new HttpEntity<>(body, copyRequestHeaders(request));

        try {
            return copyResponse(restTemplate.exchange(targetUri, method, entity, byte[].class));
        } catch (HttpStatusCodeException exception) {
            return new ResponseEntity<>(
                exception.getResponseBodyAsByteArray(),
                copyHeaders(exception.getResponseHeaders()),
                exception.getStatusCode());
        }
    }

    private URI buildTargetUri(String serviceName, HttpServletRequest request) {
        String baseUri = remoteServicesProperties.getServiceUri(serviceName);
        StringBuilder targetUri = new StringBuilder(baseUri);
        if (targetUri.charAt(targetUri.length() - 1) == '/' && request.getRequestURI().startsWith("/")) {
            targetUri.deleteCharAt(targetUri.length() - 1);
        }
        targetUri.append(request.getRequestURI());
        if (request.getQueryString() != null) {
            targetUri.append('?').append(request.getQueryString());
        }
        return URI.create(targetUri.toString());
    }

    private static HttpHeaders copyRequestHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return headers;
        }

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!isHopByHopHeader(headerName)) {
                Collections.list(request.getHeaders(headerName))
                    .forEach(value -> headers.add(headerName, value));
            }
        }
        return headers;
    }

    private static ResponseEntity<byte[]> copyResponse(ResponseEntity<byte[]> response) {
        return new ResponseEntity<>(
            response.getBody(),
            copyHeaders(response.getHeaders()),
            response.getStatusCode());
    }

    private static HttpHeaders copyHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        source.forEach((name, values) -> {
            if (!isHopByHopHeader(name)) {
                headers.put(name, new ArrayList<>(values));
            }
        });
        return headers;
    }

    private static boolean isHopByHopHeader(String name) {
        return HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
