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

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class NetworkModificationGatewayConfig {
    private static final String NETWORK_MODIFICATION_SERVER = "network-modification-server";
    private static final String API_PREFIX = "/v1";
    private static final String UUID = "uuid";

    @Bean
    public RouterFunction<ServerResponse> networkModificationGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                           ElementAccessGatewayFilter accessFilter) {
        URI networkModificationServerUri = URI.create(remoteServicesProperties.getServiceUri(NETWORK_MODIFICATION_SERVER));

        RouterFunction<ServerResponse> forwardingRoutes = route("network_modification_forwarding")
            .GET(API_PREFIX + "/network-modifications/catalog/line_types", http())
            .GET(API_PREFIX + "/network-modifications/catalog/line_types/{uuid}", http())
            .GET(API_PREFIX + "/network-modifications/catalog/line_types/{uuid}/with-limits", http())
            .GET(API_PREFIX + "/network-composite-modifications/network-modifications", http())
            .GET(API_PREFIX + "/network-modifications/busbar-sections-for-new-coupler", http())
            .PUT(API_PREFIX + "/network-modifications", http())
            .before(uri(networkModificationServerUri))
            .build();

        RouterFunction<ServerResponse> securedForwardingRoutes = route("secured_network_modification_forwarding")
            .GET(API_PREFIX + "/network-modifications/{uuid}", http())
            .PUT(API_PREFIX + "/network-modifications/{uuid}", http())
            .before(uri(networkModificationServerUri))
            .before(accessFilter.checkElementAccess(UUID))
            .build();

        return forwardingRoutes.and(securedForwardingRoutes);
    }
}
