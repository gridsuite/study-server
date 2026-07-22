package org.gridsuite.study.server.dto.studyexport;

import java.util.Map;

public record RootNetworkExportInfos(
        String name,
        String tag,
        String caseFormat,
        CaseExportInfos caseInfos,
        Map<String, Object> importParameters
) {
}
