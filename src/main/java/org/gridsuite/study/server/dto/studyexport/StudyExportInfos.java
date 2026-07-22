package org.gridsuite.study.server.dto.studyexport;

import java.util.List;
import java.util.UUID;

public record StudyExportInfos(
        UUID studyUuid,
        List<RootNetworkExportInfos> rootNetworks,
        NodeTreeExportInfos nodeTree
) {
}
