package org.gridsuite.study.server.repository.studylayout;

import org.gridsuite.study.server.dto.studylayout.diagramlayout.SubstationDiagramParams;

public class SubstationDiagramParamsEntity extends AbstractDiagramParamsEntity {
    String substationId;

    public SubstationDiagramParams toDiagramParamsDto() {
        return SubstationDiagramParams.builder()
            .diagramUuid(diagramUuid)
            .substationId(substationId)
            .build();
    }
}
