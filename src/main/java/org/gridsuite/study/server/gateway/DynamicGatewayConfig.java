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
public class DynamicGatewayConfig {
    private static final String API_PREFIX = "/v1";
    private static final String PARAMETER_UUID = "parameterUuid";
    private static final String MAPPING_ID = "mappingId";

    @Bean
    public RouterFunction<ServerResponse> dynamicSimulationGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                         ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("dynamic-simulation-server"));

        return route("dynamic_simulation_forwarding")
            .GET(API_PREFIX + "/dynamic-simulation/providers", http())
            .GET(API_PREFIX + "/dynamic-simulation/results/{resultUuid}/download-debug-file", http())
            .before(uri(uri))
            .build()
            .and(route("secured_dynamic_simulation_forwarding")
                .GET(API_PREFIX + "/dynamic-simulation/parameters/{parameterUuid}", http())
                .PUT(API_PREFIX + "/dynamic-simulation/parameters/{parameterUuid}", http())
                .before(uri(uri))
                .before(accessFilter.checkElementAccess(PARAMETER_UUID))
                .build());
    }

    @Bean
    public RouterFunction<ServerResponse> dynamicSecurityAnalysisGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                               ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("dynamic-security-analysis-server"));

        return route("dynamic_security_analysis_forwarding")
            .GET(API_PREFIX + "/dynamic-security-analysis/providers", http())
            .GET(API_PREFIX + "/dynamic-security-analysis/results/{resultUuid}/download-debug-file", http())
            .before(uri(uri))
            .build()
            .and(route("secured_dynamic_security_analysis_forwarding")
                .GET(API_PREFIX + "/dynamic-security-analysis/parameters/{parameterUuid}", http())
                .PUT(API_PREFIX + "/dynamic-security-analysis/parameters/{parameterUuid}", http())
                .before(uri(uri))
                .before(accessFilter.checkElementAccess(PARAMETER_UUID))
                .build());
    }

    @Bean
    public RouterFunction<ServerResponse> dynamicMarginCalculationGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                                ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("dynamic-margin-calculation-server"));

        return route("dynamic_margin_calculation_forwarding")
            .GET(API_PREFIX + "/dynamic-margin-calculation/providers", http())
            .GET(API_PREFIX + "/dynamic-margin-calculation/results/{resultUuid}/download-debug-file", http())
            .before(uri(uri))
            .build()
            .and(route("secured_dynamic_margin_calculation_forwarding")
                .GET(API_PREFIX + "/dynamic-margin-calculation/parameters/{parameterUuid}", http())
                .PUT(API_PREFIX + "/dynamic-margin-calculation/parameters/{parameterUuid}", http())
                .before(uri(uri))
                .before(accessFilter.checkElementAccess(PARAMETER_UUID))
                .build());
    }

    @Bean
    public RouterFunction<ServerResponse> dynamicMappingGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                      ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("dynamic-mapping-server"));

        return route("secured_dynamic_mapping_forwarding")
            .GET(API_PREFIX + "/dynamic-mapping/mappings/{mappingId}/models", http())
            .before(uri(uri))
            .before(rewritePath(API_PREFIX + "/dynamic-mapping/(?<segment>.*)", "/${segment}"))
            .before(accessFilter.checkElementAccess(MAPPING_ID))
            .build();
    }
}
