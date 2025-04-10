package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@Getter
@Setter
public class RootNetworkInfos {
    private UUID id;

    private String name;

    private List<RootNetworkNodeInfo> rootNetworkNodeInfos;

    private NetworkInfos networkInfos;

    private CaseInfos caseInfos;

    // reportUuid of network import, root node one
    private UUID reportUuid;

    private Map<String, String> importParameters;

    private String tag;

    public RootNetworkEntity toEntity() {
        RootNetworkEntity.RootNetworkEntityBuilder rootNetworkEntityBuilder = RootNetworkEntity.builder()
            .id(id)
            .name(name)
            .networkUuid(networkInfos.getNetworkUuid())
            .networkId(networkInfos.getNetworkId())
            .caseUuid(caseInfos.getCaseUuid())
            .caseName(caseInfos.getCaseName())
            .caseFormat(caseInfos.getCaseFormat())
            .reportUuid(reportUuid)
            .importParameters(importParameters)
            .indexationStatus(RootNetworkIndexationStatus.INDEXED)
            .tag(tag);

        if (rootNetworkNodeInfos != null) {
            rootNetworkEntityBuilder.rootNetworkNodeInfos(rootNetworkNodeInfos.stream().map(RootNetworkNodeInfo::toEntity).toList());
        }

        return rootNetworkEntityBuilder.build();
    }
}
