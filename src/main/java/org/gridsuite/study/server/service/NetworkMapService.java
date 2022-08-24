package org.gridsuite.study.server.service;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.NETWORK_MAP_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;
import static org.gridsuite.study.server.StudyException.Type.EQUIPMENT_NOT_FOUND;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.IdentifiableInfos;
import org.gridsuite.study.server.dto.VoltageLevelMapData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class NetworkMapService {

    static final String QUERY_PARAM_SUBSTATION_ID = "substationId";

    @Autowired
    private RestTemplate restTemplate;

    private String networkMapServerBaseUri;

    @Autowired
    public NetworkMapService(@Value("${backing-services.network-map.base-uri:http://network-map-server/}") String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }

    public String getEquipmentsMapData(UUID networkUuid, String variantId, List<String> substationsIds, String equipmentPath) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath);
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATION_ID, substationsIds);
        }
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid).toUriString();

        return restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
    }

    public String getEquipmentMapData(UUID networkUuid, String variantId, String equipmentPath, String equipmentId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath + "/{equipmentUuid}");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, equipmentId).toUriString();

        String equipmentMapData;
        try {
            equipmentMapData = restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(EQUIPMENT_NOT_FOUND);
            } else {
                throw e;
            }
        }
        return equipmentMapData;
    }

    public List<VoltageLevelMapData> getVoltageLevelMapData(UUID networkUuid, String variantId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/voltage-levels");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid).toUriString();

        return restTemplate.exchange(networkMapServerBaseUri + path,
                HttpMethod.GET, null, new ParameterizedTypeReference<List<VoltageLevelMapData>>() {
                }).getBody();
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID networkUuid, String variantId, String voltageLevelId,
            String busPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/" + busPath);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, voltageLevelId).toUriString();

        return restTemplate.exchange(networkMapServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<IdentifiableInfos>>() {
                }).getBody();
    }

    public void setNetworkMapServerBaseUri(String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }
}
