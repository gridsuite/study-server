package org.gridsuite.study.server.service.common;

import org.gridsuite.study.server.service.client.AbstractRestClientService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RESULTS_UUIDS;

public abstract class AbstractComputationRestService extends AbstractRestClientService {

    protected AbstractComputationRestService(String baseUri, RestClient restClient) {
        super(baseUri, restClient);
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
}
