package org.gridsuite.study.server.service.common;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.RemoteServicesInspector;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.RemoteServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RESULTS_UUIDS;

public abstract class AbstractComputationRestService extends AbstractRestClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractComputationRestService.class);
    private final RemoteServicesInspector servicesInspector;
    private final RemoteServiceName serviceName;

    protected AbstractComputationRestService(String baseUri, RestTemplate restTemplate,
                                             RemoteServicesInspector servicesInspector,
                                             RemoteServiceName serviceName) {
        super(baseUri, restTemplate);
        this.servicesInspector = servicesInspector;
        this.serviceName = serviceName;
    }

    public boolean isOptionalAndDown() {
        RemoteServicesProperties.Service service = servicesInspector.getService(serviceName);
        if (service == null || !service.isOptional()) {
            return false;
        }
        return !servicesInspector.isOptionalServiceUpSync(service);
    }

    protected void executeWithOptionalServiceCheck(VoidRestExecutor executor) {
        try {
            executor.execute();
        } catch (Exception e) {
            if (isOptionalAndDown()) {
                // the error is not thrown and won't be displayed in the console :
                // because this is an optional service, it may be down and ignored
                LOGGER.debug("The service '{}' threw the error '{}'. It is silenced because this service is optional.", serviceName, e.toString());
            } else {
                throw e;
            }
        }
    }

    protected <T> T executeWithOptionalServiceFallback(RestExecutor<T> executor, T fallbackReturnValue) {
        try {
            return executor.execute();
        } catch (Exception e) {
            if (isOptionalAndDown()) {
                // the error is not thrown and won't be displayed in the console :
                // because this is an optional service, it may be down and ignored
                LOGGER.debug("The service '{}' threw the error '{}'. It is silenced because this service is optional.", serviceName, e.toString());
                return fallbackReturnValue;
            }
            throw e;
        }
    }

    @FunctionalInterface
    public interface RestExecutor<T> {
        T execute();
    }

    @FunctionalInterface
    public interface VoidRestExecutor {
        void execute();
    }

    public abstract List<String> getEnumValues(String enumName, UUID resultUuidOpt);

    public List<String> getEnumValues(String enumName, UUID resultUuid, String apiVersion, RestTemplate restTemplate) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + apiVersion + "/results/{resultUuid}/{enumName}");
        String path = uriComponentsBuilder.buildAndExpand(resultUuid, enumName).toUriString();
        return restTemplate.exchange(baseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<String>>() { }).getBody();
    }

    public static void deleteCalculationResults(List<UUID> resultsUuids,
                                                String path,
                                                RestTemplate restTemplate,
                                                String serverBaseUri) {
        if (resultsUuids != null && resultsUuids.isEmpty()) {
            return;
        }
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(path).queryParam(QUERY_PARAM_RESULTS_UUIDS, resultsUuids);
        restTemplate.delete(serverBaseUri + uriComponentsBuilder.build().toUriString());
    }

    public static void deleteCalculationResults(List<UUID> resultsUuids,
                                                String path,
                                                RestTemplate restTemplate,
                                                String serverBaseUri,
                                                String userId) {
        if (resultsUuids != null && resultsUuids.isEmpty()) {
            return;
        }
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(path).queryParam(QUERY_PARAM_RESULTS_UUIDS, resultsUuids);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        restTemplate.exchange(serverBaseUri + uriComponentsBuilder.build().toUriString(), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
}
