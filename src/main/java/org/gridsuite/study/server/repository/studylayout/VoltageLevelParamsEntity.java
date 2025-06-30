package org.gridsuite.study.server.repository.studylayout;

import org.gridsuite.study.server.dto.studylayout.diagramlayout.VoltageLevelDiagramParams;

public class VoltageLevelParamsEntity extends AbstractDiagramParamsEntity {
    String voltageLevelId;

    public VoltageLevelDiagramParams toDiagramParamsDto() {
        return VoltageLevelDiagramParams.builder()
            .diagramUuid(diagramUuid)
            .voltageLevelId(voltageLevelId)
            .build();
    }
}
