package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudyExportInfos(UUID studyUuid,
                               List<RootNetworkExportInfos> rootNetworks,
                               StudyTreeNodeExportInfos rootNode) {
}
