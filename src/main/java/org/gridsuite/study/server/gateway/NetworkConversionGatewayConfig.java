/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class NetworkConversionGatewayConfig {
    private static final String API_PREFIX = "/v1";
    private static final String CASE_UUID = "caseUuid";

    @Bean
    public RouterFunction<ServerResponse> networkConversionGatewayRoutes(
            @Value("${powsybl.services.network-conversion-server.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
            ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(networkConversionServerBaseUri);

        return route("secured_network_conversion_forwarding")
            .GET(API_PREFIX + "/network-conversion/cases/{caseUuid}/import-parameters", http())
            .before(uri(uri))
            .before(rewritePath(API_PREFIX + "/network-conversion/(?<segment>.*)", API_PREFIX + "/${segment}"))
            .before(accessFilter.checkElementAccess(CASE_UUID))
            .build();
    }
}
