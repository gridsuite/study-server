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

public abstract class AbstractGenericComputingTypeService {

    public List<String> getFilterEnumValues(String filterEnum, UUID resultUuidOpt, String apiVersion,
                                            String computingTypeBaseUri, StudyException.Type type, RestTemplate restTemplate) {
        List<String> result;
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + apiVersion + "/results/{resultUuid}/{filterEnum}");
        String path = uriComponentsBuilder.buildAndExpand(resultUuidOpt, filterEnum).toUriString();
        try {
            ResponseEntity<List<String>> responseEntity = restTemplate.exchange(computingTypeBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
            });
            result = responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(type);
            }
            throw e;
        }
        return result;
    }
}
