/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */

import java.util.Objects;
import java.util.UUID;

import com.powsybl.shortcircuit.ShortCircuitAnalysisResult;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.ShortCircuitStatus;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class ShortCircuitService {
    private String shortCircuitServerBaseUri;

    @Autowired
    NotificationService notificationService;

    private final NetworkService networkStoreService;

    NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public ShortCircuitService(
            @Value("${backing-services.shortcircuit.base-uri:http://shortcircuit-server") String shortCircuitServerBaseUri,
            NetworkModificationTreeService networkModificationTreeService,
            NetworkService networkStoreService) {
        this.shortCircuitServerBaseUri = shortCircuitServerBaseUri;
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    public void runShortCircuit(UUID studyUuid, UUID nodeUuid, ShortCircuitParameters shortCircuitParameters, String provider) {
        try {
            ShortCircuitAnalysisResult result;

            UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
            String variantId = getVariantId(nodeUuid);
            UUID reportUuid = getReportUuid(nodeUuid);

            var uriComponentsBuilder = UriComponentsBuilder
                    .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/networks/{networkUuid}/run")
                    .queryParam("reportId", reportUuid.toString())
                    .queryParam("reportName", "shortcircuit");
            if (!provider.isEmpty()) {
                uriComponentsBuilder.queryParam("provider", provider);
            }
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ShortCircuitParameters> httpEntity = new HttpEntity<>(shortCircuitParameters, headers);

//            setLoadFlowRunning(studyUuid, nodeUuid);
            ResponseEntity<ShortCircuitAnalysisResult> resp = restTemplate.exchange(shortCircuitServerBaseUri + path, HttpMethod.PUT,
                    httpEntity, ShortCircuitAnalysisResult.class);
            result = resp.getBody();
            updatShortCircuitResultAndStatus(nodeUuid, result, false);
        } catch (Exception e) {
            //updateShortCircuitStatus(nodeUuid, ShortCircuitStatus.NOT_DONE);
            throw e;
        } finally {
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT);
        }
    }

    private void updatShortCircuitResultAndStatus(UUID nodeUuid, ShortCircuitAnalysisResult shortCircuitAnalysisResult, boolean updateChildren) {
        networkModificationTreeService.updateShortCircuitAnalysisResultAndStatus(nodeUuid, shortCircuitAnalysisResult, updateChildren);
    }

    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }

    public static ShortCircuitParameters fromEntity(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        ShortCircuitParameters parameters = new ShortCircuitParameters();
        parameters.setWithLimitViolations(entity.isWithLimitViolations());
        parameters.setWithVoltageMap(entity.isWithVoltageMap());
        parameters.setWithFeederResult(entity.isWithFeederResult());
        parameters.setStudyType(entity.getStudyType());
        parameters.setMinVoltageDropProportionalThreshold(entity.getMinVoltageDropProportionalThreshold());
        return parameters;
    }

    public static ShortCircuitParametersEntity toEntity(ShortCircuitParameters parameters) {
        Objects.requireNonNull(parameters);
        return new ShortCircuitParametersEntity(null,
                parameters.isWithLimitViolations(),
                parameters.isWithVoltageMap(),
                parameters.isWithFeederResult(),
                parameters.getStudyType(),
                parameters.getMinVoltageDropProportionalThreshold()
        );
    }

    public void setShortCircuitServerBaseUri(String shortCircuitServerBaseUri) {
        this.shortCircuitServerBaseUri = shortCircuitServerBaseUri;
    }
}
