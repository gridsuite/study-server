package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@Getter
@Setter
public class RootNetworkInfos {
    private UUID id;

    private String name;

    private String description;

    private List<RootNetworkNodeInfo> rootNetworkNodeInfos;

    private NetworkInfos networkInfos;

    private CaseInfos caseInfos;

    // reportUuid of network import, root node one
    private UUID reportUuid;

    private Map<String, Object> importParameters;

    private Map<String, Object> importParametersRaw;

    private String tag;

    public RootNetworkEntity toEntity() {
        RootNetworkEntity.RootNetworkEntityBuilder rootNetworkEntityBuilder = RootNetworkEntity.builder()
                .id(id)
                .name(name)
                .description(description)
                .networkUuid(networkInfos.getNetworkUuid())
                .networkId(networkInfos.getNetworkId())
                .caseUuid(caseInfos.getCaseUuid())
                .originalCaseUuid(caseInfos.getOriginalCaseUuid())
                .caseName(caseInfos.getCaseName())
                .caseFormat(caseInfos.getCaseFormat())
                .reportUuid(reportUuid)
                .importParameters(serializeImportParameters(importParameters))
                .tag(tag);

        if (rootNetworkNodeInfos != null) {
            rootNetworkEntityBuilder.rootNetworkNodeInfos(rootNetworkNodeInfos.stream().map(RootNetworkNodeInfo::toEntity).toList());
        }

        return rootNetworkEntityBuilder.build();
    }

    public static Map<String, String> serializeImportParameters(Map<String, Object> params) {
        Map<String, String> result = new HashMap<>();
        if (params == null) {
            return null;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        params.forEach((key, value) -> {
            try {
                result.put(key, objectMapper.writeValueAsString(value));
            } catch (JsonProcessingException e) {
                result.put(key, String.valueOf(value));
            }
        });
        return result;
    }

}
