package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Getter;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;

import java.util.List;
import java.util.UUID;

@Builder
@Getter
public class RootNetworkInfos {
    private UUID id;

    private List<RootNetworkNodeInfo> rootNetworkNodeInfos;

    private NetworkInfos networkInfos;

    private CaseInfos caseInfos;

    // reportUuid of network import, root node one
    private UUID reportUuid;

    public RootNetworkEntity toEntity() {
        RootNetworkEntity.RootNetworkEntityBuilder rootNetworkEntityBuilder = RootNetworkEntity.builder()
            .id(id)
            .networkUuid(networkInfos.getNetworkUuid())
            .networkId(networkInfos.getNetworkId())
            .caseUuid(caseInfos.getCaseUuid())
            .caseName(caseInfos.getCaseName())
            .caseFormat(caseInfos.getCaseFormat())
            .reportUuid(reportUuid);

        if (rootNetworkNodeInfos != null) {
            rootNetworkEntityBuilder.rootNetworkNodeInfos(rootNetworkNodeInfos.stream().map(RootNetworkNodeInfo::toEntity).toList());
        }

        return rootNetworkEntityBuilder.build();
    }
}
