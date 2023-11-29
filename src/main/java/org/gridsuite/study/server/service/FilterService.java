package org.gridsuite.study.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.FILTER_API_VERSION;

@Service
public class FilterService {

    public static final String FILTER_END_POINT_EVALUATE = "/filters/evaluate";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final RestTemplate restTemplate;

    private final String baseUri;

    @Autowired
    public FilterService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.baseUri = remoteServicesProperties.getServiceUri("filter-server");
        this.restTemplate = restTemplate;
    }

    public String evaluateFilter(UUID networkUuid, String variantId, String filter) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = baseUri + DELIMITER + FILTER_API_VERSION + FILTER_END_POINT_EVALUATE;

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
        return restTemplate.postForObject(uriComponent.toUriString(), request, String.class);
    }
}
