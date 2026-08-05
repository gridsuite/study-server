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
public class ParametersGatewayConfig {
    private static final String API_PREFIX = "/v1";
    private static final String PARAMETER_UUID = "parameterUuid";

    @Bean
    public RouterFunction<ServerResponse> loadFlowParametersGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                          ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("loadflow-server"));

        return route("loadflow_parameters_forwarding")
            .GET(API_PREFIX + "/loadflow/providers", http())
            .GET(API_PREFIX + "/loadflow/specific-parameters", http())
            .GET(API_PREFIX + "/loadflow/parameters/default-limit-reductions", http())
            .before(uri(uri))
            .build()
            .and(route("secured_loadflow_parameters_forwarding")
                .GET(API_PREFIX + "/loadflow/parameters/{parameterUuid}", http())
                .PUT(API_PREFIX + "/loadflow/parameters/{parameterUuid}", http())
                .before(uri(uri))
                .before(accessFilter.checkElementAccess(PARAMETER_UUID))
                .build());
    }

    @Bean
    public RouterFunction<ServerResponse> securityAnalysisParametersGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                                  ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("security-analysis-server"));

        return route("security_analysis_parameters_forwarding")
            .GET(API_PREFIX + "/security-analysis/providers", http())
            .GET(API_PREFIX + "/security-analysis/parameters/default-limit-reductions", http())
            .before(uri(uri))
            .build()
            .and(route("secured_security_analysis_parameters_forwarding")
                .GET(API_PREFIX + "/security-analysis/parameters/{parameterUuid}", http())
                .PUT(API_PREFIX + "/security-analysis/parameters/{parameterUuid}", http())
                .before(uri(uri))
                .before(accessFilter.checkElementAccess(PARAMETER_UUID))
                .build());
    }

    @Bean
    public RouterFunction<ServerResponse> shortCircuitParametersGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                              ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("shortcircuit-server"));

        return route("shortcircuit_parameters_forwarding")
            .GET(API_PREFIX + "/shortcircuit/results/{resultUuid}/download-debug-file", http())
            .GET(API_PREFIX + "/shortcircuit/parameters/specific-parameters", http())
            .before(uri(uri))
            .build()
            .and(route("secured_shortcircuit_parameters_forwarding")
                .GET(API_PREFIX + "/shortcircuit/parameters/{parameterUuid}", http())
                .PUT(API_PREFIX + "/shortcircuit/parameters/{parameterUuid}", http())
                .before(uri(uri))
                .before(accessFilter.checkElementAccess(PARAMETER_UUID))
                .build());
    }

    @Bean
    public RouterFunction<ServerResponse> sensitivityAnalysisParametersGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                                     ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("sensitivity-analysis-server"));

        return route("sensitivity_analysis_parameters_forwarding")
            .GET(API_PREFIX + "/sensitivity-analysis/providers", http())
            .before(uri(uri))
            .build()
            .and(route("secured_sensitivity_analysis_parameters_forwarding")
                .GET(API_PREFIX + "/sensitivity-analysis/parameters/{parameterUuid}", http())
                .PUT(API_PREFIX + "/sensitivity-analysis/parameters/{parameterUuid}", http())
                .before(uri(uri))
                .before(accessFilter.checkElementAccess(PARAMETER_UUID))
                .build());
    }

    @Bean
    public RouterFunction<ServerResponse> pccMinParametersGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                        ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("pcc-min-server"));

        return route("secured_pcc_min_parameters_forwarding")
            .GET(API_PREFIX + "/pcc-min/parameters/{parameterUuid}", http())
            .before(uri(uri))
            .before(accessFilter.checkElementAccess(PARAMETER_UUID))
            .build();
    }

    @Bean
    public RouterFunction<ServerResponse> voltageInitParametersGatewayRoutes(RemoteServicesProperties remoteServicesProperties,
                                                                             ElementAccessGatewayFilter accessFilter) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("voltage-init-server"));

        return route("voltage_init_parameters_forwarding")
            .GET(API_PREFIX + "/voltage-init/results/{resultUuid}/download-debug-file", http())
            .before(uri(uri))
            .build()
            .and(route("secured_voltage_init_parameters_forwarding")
                .GET(API_PREFIX + "/voltage-init/parameters/{parameterUuid}", http())
                .before(uri(uri))
                .before(accessFilter.checkElementAccess(PARAMETER_UUID))
                .build());
    }

    @Bean
    public RouterFunction<ServerResponse> stateEstimationParametersGatewayRoutes(RemoteServicesProperties remoteServicesProperties) {
        URI uri = URI.create(remoteServicesProperties.getServiceUri("state-estimation-server"));

        return route("state_estimation_parameters_forwarding")
            .GET(API_PREFIX + "/state-estimation/results/{resultUuid}/download-debug-file", http())
            .before(uri(uri))
            .build();
    }
}
