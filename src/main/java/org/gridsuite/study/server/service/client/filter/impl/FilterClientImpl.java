package org.gridsuite.study.server.service.client.filter.impl;

import org.gridsuite.study.server.service.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.filter.FilterClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

@Service
public class FilterClientImpl extends AbstractRestClient implements FilterClient {

    public FilterClientImpl(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("filter-server"), restTemplate);
    }

    @Override
    public String evaluateFilter(UUID networkUuid, String variantId, String filter) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, FILTER_END_POINT_EVALUATE);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl);
        uriComponentsBuilder.queryParam("networkUuid", networkUuid);
        if (variantId != null && !variantId.isBlank()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        var uriComponent = uriComponentsBuilder
                .build();

        // call filter-server REST API
        return getRestTemplate().postForObject(uriComponent.toUriString(), filter, String.class);
    }
}
