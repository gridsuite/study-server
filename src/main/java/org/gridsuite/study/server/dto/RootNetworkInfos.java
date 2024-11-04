package org.gridsuite.study.server.dto;

import org.gridsuite.study.server.repository.StudyEntity;

import java.util.List;
import java.util.UUID;

public class RootNetworkInfos {
    private UUID id;

    private StudyEntity study;

    private List<RootNetworkNodeInfo> rootNetworkNodeInfos;

    private UUID networkUuid;

    private String networkId;

    private String caseFormat;

    private UUID caseUuid;

    private String caseName;

    // reportUuid of network import, root node one
    private UUID reportUuid;
}
