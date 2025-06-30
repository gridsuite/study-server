package org.gridsuite.study.server.dto.studylayout.diagramlayout;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public class VoltageLevelDiagramParams extends AbstractDiagramParams {
    String voltageLevelId;

    @Override
    public DiagramLayoutType getDiagramType() {
        return DiagramLayoutType.VOLTAGE_LEVEL;
    }
}
