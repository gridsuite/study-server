/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.gateway;

import org.gridsuite.study.server.RemoteServicesProperties;
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
public class NetworkMapGatewayConfig {
    private static final String API_PREFIX = "/v1";

    @Bean
    public RouterFunction<ServerResponse> networkMapGatewayRoutes(RemoteServicesProperties remoteServicesProperties) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("network-map-server"));

        return route("network_map_forwarding")
            .GET(API_PREFIX + "/network-map/schemas/{elementType}/{infoType}", http())
            .before(uri(uri))
            .before(rewritePath(API_PREFIX + "/network-map/(?<segment>.*)", API_PREFIX + "/${segment}"))
            .build();
    }
}
