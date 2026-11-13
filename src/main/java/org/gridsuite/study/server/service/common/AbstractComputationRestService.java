/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.common;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.RemoteServicesInspector;
import org.gridsuite.study.server.service.client.AbstractRestClientService;
import org.gridsuite.study.server.service.client.RemoteServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RESULTS_UUIDS;

public abstract class AbstractComputationRestService extends AbstractRestClientService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractComputationRestService.class);
    private final RemoteServicesInspector servicesInspector;
    private final RemoteServiceName serviceName;

    protected AbstractComputationRestService(String baseUri,
                                             RestClient restClient,
                                             RemoteServicesInspector servicesInspector,
                                             RemoteServiceName serviceName) {
        super(baseUri, restClient);
        this.servicesInspector = servicesInspector;
        this.serviceName = serviceName;
    }

    public boolean isOptionalAndDown() {
        RemoteServicesProperties.Service service = servicesInspector.getService(serviceName);
        if (service == null || !service.isOptional()) {
            return false;
        }
        return !servicesInspector.isServiceUp(service);
    }

    protected void callWithOptionalServer(Runnable request) {
        callWithOptionalServer(asSupplier(request), null);
    }

    protected <T> T callWithOptionalServerAndDefaultValue(Supplier<T> request) {
        return callWithOptionalServer(request, null);
    }

    private <T> T callWithOptionalServer(Supplier<T> request, T defaultValue) {
        try {
            return request.get();
        } catch (Exception e) {
            if (isOptionalAndDown()) {
                // the error is not thrown and won't be displayed in the console :
                // because this is an optional service, it may be down and ignored
                LOGGER.debug("The service '{}' threw the error '{}'. It is silenced because this service is optional.", serviceName, e.toString());
                return defaultValue;
            }
            throw e;
        }
    }

    private static <T> Supplier<T> asSupplier(Runnable request) {
        return () -> {
            request.run();
            return null;
        };
    }

    public abstract List<String> getEnumValues(String enumName, UUID resultUuidOpt);

    public List<String> getEnumValues(String enumName, UUID resultUuid, String apiVersion, RestClient restClient) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + apiVersion + "/results/{resultUuid}/{enumName}");
        String path = uriComponentsBuilder.buildAndExpand(resultUuid, enumName).toUriString();
        return restClient.get().uri(baseUri + path)
                .retrieve().body(new ParameterizedTypeReference<List<String>>() { });
    }

    public static void deleteCalculationResults(List<UUID> resultsUuids,
                                                String path,
                                                RestClient restClient,
                                                String serverBaseUri) {
        if (resultsUuids != null && resultsUuids.isEmpty()) {
            return;
        }
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(path).queryParam(QUERY_PARAM_RESULTS_UUIDS, resultsUuids);
        restClient.delete().uri(serverBaseUri + uriComponentsBuilder.build().toUriString())
                .retrieve().toBodilessEntity();
    }

    public static void deleteCalculationResults(List<UUID> resultsUuids,
                                                String path,
                                                RestClient restClient,
                                                String serverBaseUri,
                                                String userId) {
        if (resultsUuids != null && resultsUuids.isEmpty()) {
            return;
        }
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(path).queryParam(QUERY_PARAM_RESULTS_UUIDS, resultsUuids);
        restClient.delete().uri(serverBaseUri + uriComponentsBuilder.build().toUriString())
                .headers(headers -> headers.set(HEADER_USER_ID, userId))
                .retrieve().toBodilessEntity();
    }
}
