package org.gridsuite.study.server.dto.studyexport;

import java.util.UUID;

public record CaseExportInfos(
        UUID uuid,
        String name
) {
}
