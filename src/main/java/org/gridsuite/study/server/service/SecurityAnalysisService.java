package org.gridsuite.study.server.service;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;
import static org.gridsuite.study.server.StudyConstants.SECURITY_ANALYSIS_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.SECURITY_ANALYSIS_NOT_FOUND;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SecurityAnalysisService {

    static final String QUERY_PARAM_RECEIVER = "receiver";
    static final String RESULT_UUID = "resultUuid";

    @Autowired
    private RestTemplate restTemplate;

    private String securityAnalysisServerBaseUri;

    private final NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    public SecurityAnalysisService(@Value("${backing-services.security-analysis-server.base-uri:http://security-analysis-server/}") String securityAnalysisServerBaseUri,
            NetworkModificationTreeService networkModificationTreeService) {
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    public String getSecurityAnalysisResult(UUID nodeUuid, List<String> limitTypes) {
        Objects.requireNonNull(limitTypes);
        String result = null;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
                .queryParam("limitType", limitTypes).buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            result = restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            } else {
                throw e;
            }
        }

        return result;
    }

    public UUID runSecurityAnalysis(UUID networkUuid, UUID reportUuid, String variantId, String provider, List<String> contingencyListNames, String parameters,
            String receiver) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam("reportUuid", reportUuid.toString());
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.queryParam("contingencyListName", contingencyListNames)
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        return restTemplate
                .exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getSecurityAnalysisStatus(UUID resultUuid) {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
                .buildAndExpand(resultUuid).toUriString();

        return restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
    }

    public void stopSecurityAnalysis(UUID resultUuid, String receiver) {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
    }

    public void deleteSaResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(securityAnalysisServerBaseUri + path);
    }

    public void invalidateSaStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
        }
    }
}
