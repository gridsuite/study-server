package org.gridsuite.study.server.dto.studylayout.diagramlayout;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public class SubstationDiagramParams extends AbstractDiagramParams {
    String substationId;

    @Override
    public DiagramLayoutType getDiagramType() {
        return DiagramLayoutType.SUBSTATION;
    }
}
