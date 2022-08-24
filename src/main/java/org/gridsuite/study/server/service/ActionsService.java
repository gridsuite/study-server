package org.gridsuite.study.server.service;

import static org.gridsuite.study.server.StudyConstants.ACTIONS_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.powsybl.contingency.Contingency;

@Service
public class ActionsService {

    @Autowired
    private RestTemplate restTemplate;

    private String actionsServerBaseUri;

    @Autowired
    public ActionsService(@Value("${backing-services.actions-server.base-uri:http://actions-server/}") String actionsServerBaseUri) {
        this.actionsServerBaseUri = actionsServerBaseUri;
    }

    public Integer getContingencyCount(UUID networkUuid, String variantId, List<String> contingencyListNames) {
        return contingencyListNames.stream().map(contingencyListName -> {
            var uriComponentsBuilder = UriComponentsBuilder
                    .fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/{contingencyListName}/export")
                    .queryParam("networkUuid", networkUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder.buildAndExpand(contingencyListName).toUriString();

            List<Contingency> contingencies = restTemplate.exchange(actionsServerBaseUri + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Contingency>>() {
                    }).getBody();

            return contingencies.size();
        }).reduce(0, Integer::sum);
    }
}
