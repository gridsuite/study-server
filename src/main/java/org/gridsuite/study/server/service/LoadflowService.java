package org.gridsuite.study.server.service;

import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.LoadFlowStatus;
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

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class LoadflowService {
    private String loadFlowServerBaseUri;

    @Autowired
    NotificationService notificationService;

    private final NetworkService networkStoreService;

    NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public LoadflowService(
        @Value("${backing-services.loadflow.base-uri:http://loadflow-server/}") String loadFlowServerBaseUri,
        NetworkModificationTreeService networkModificationTreeService,
        NetworkService networkStoreService) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
        this.networkStoreService = networkStoreService;
    }

    void setLoadFlowServerBaseUri(String loadFlowServerBaseUri) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
    }

    public void runLoadFlow(UUID studyUuid, UUID nodeUuid, LoadFlowParameters loadflowParameters, String provider) {
        try {
            LoadFlowResult result;

            UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
            String variantId = getVariantId(nodeUuid);
            UUID reportUuid = getReportUuid(nodeUuid);

            var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                .queryParam("reportId", reportUuid.toString())
                .queryParam("reportName", "loadflow");
            if (!provider.isEmpty()) {
                uriComponentsBuilder.queryParam("provider", provider);
            }
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<LoadFlowParameters> httpEntity = new HttpEntity<>(loadflowParameters, headers);

            setLoadFlowRunning(studyUuid, nodeUuid);
            ResponseEntity<LoadFlowResult> resp = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.PUT,
                httpEntity, LoadFlowResult.class);
            result = resp.getBody();
            updateLoadFlowResultAndStatus(nodeUuid, result, computeLoadFlowStatus(result), false);
        } catch (Exception e) {
            updateLoadFlowStatus(nodeUuid, LoadFlowStatus.NOT_DONE);
            throw e;
        } finally {
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW);
        }
    }

    public void setLoadFlowRunning(UUID studyUuid, UUID nodeUuid) {
        updateLoadFlowStatus(nodeUuid, LoadFlowStatus.RUNNING);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
    }

    void updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        networkModificationTreeService.updateLoadFlowStatus(nodeUuid, loadFlowStatus);
    }

    private LoadFlowStatus computeLoadFlowStatus(LoadFlowResult result) {
        return result.getComponentResults().stream()
                .filter(cr -> cr.getConnectedComponentNum() == 0 && cr.getSynchronousComponentNum() == 0
                        && cr.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED)
                .collect(Collectors.toList()).isEmpty() ? LoadFlowStatus.DIVERGED : LoadFlowStatus.CONVERGED;
    }

    private void updateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult,
            LoadFlowStatus loadFlowStatus, boolean updateChildren) {
        networkModificationTreeService.updateLoadFlowResultAndStatus(nodeUuid, loadFlowResult, loadFlowStatus,
                updateChildren);
    }

    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }
}
