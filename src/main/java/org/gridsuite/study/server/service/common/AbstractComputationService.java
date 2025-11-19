package org.gridsuite.study.server.service.common;

import org.gridsuite.study.server.StudyException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RESULTS_UUIDS;
import static org.gridsuite.study.server.StudyException.Type.DELETE_COMPUTATION_RESULTS_FAILED;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

public abstract class AbstractComputationService {

    public abstract List<String> getEnumValues(String enumName, UUID resultUuidOpt);

    public List<String> getEnumValues(String enumName, UUID resultUuid, String apiVersion, String computingTypeBaseUri, StudyException.Type type, RestTemplate restTemplate) {
        List<String> result;
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + apiVersion + "/results/{resultUuid}/{enumName}");
        String path = uriComponentsBuilder.buildAndExpand(resultUuid, enumName).toUriString();
        try {
            ResponseEntity<List<String>> responseEntity = restTemplate.exchange(computingTypeBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<>() { });
            result = responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(type);
            }
            throw e;
        }
        return result;
    }

    public static void deleteCalculationResults(List<UUID> resultsUuids,
                                                String path,
                                                RestTemplate restTemplate,
                                                String serverBaseUri) {
        if (resultsUuids != null && resultsUuids.isEmpty()) {
            return;
        }
        try {
            UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(path).queryParam(QUERY_PARAM_RESULTS_UUIDS, resultsUuids);
            restTemplate.delete(serverBaseUri + uriComponentsBuilder.build().toUriString());
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }
}
