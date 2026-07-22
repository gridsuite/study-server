package org.gridsuite.study.server.dto.studyexport;

import java.util.List;
import java.util.UUID;

public record NodeTreeExportInfos(
        UUID id,
        String name,
        String type,
        UUID modificationGroupUuid,
        String buildStatus,
        List<NodeTreeExportInfos> children
) {
}
