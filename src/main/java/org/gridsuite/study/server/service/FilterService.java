package org.gridsuite.study.server.service;

import org.gridsuite.study.server.StudyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.FILTER_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

@Service
public class FilterService {

    public static final String FILTER_END_POINT_EVALUATE = "/filters/evaluate";

    private final RestTemplate restTemplate;

    private final String baseUri;

    // getter to facilitate to mock
    public String getBaseUri() {
        return baseUri;
    }

    @Autowired
    public FilterService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.baseUri = remoteServicesProperties.getServiceUri("filter-server");
        this.restTemplate = restTemplate;
    }

    public String evaluateFilter(UUID networkUuid, String variantId, String filter) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = getBaseUri() + DELIMITER + FILTER_API_VERSION + FILTER_END_POINT_EVALUATE;

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl);
        uriComponentsBuilder.queryParam("networkUuid", networkUuid);
        if (variantId != null && !variantId.isBlank()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        var uriComponent = uriComponentsBuilder
                .build();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(filter, headers);

        // call filter-server REST API
        try {
            return restTemplate.postForObject(uriComponent.toUriString(), request, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NETWORK_NOT_FOUND);
            } else {
                throw handleHttpError(e, EVALUATE_FILTER_FAILED);
            }
        }
    }
}