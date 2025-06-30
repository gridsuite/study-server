package org.gridsuite.study.server.dto.studylayout.diagramlayout;

import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
public class NetworkAreaDiagramParams extends AbstractDiagramParams {
    List<String> voltageLevelIds;
    Integer depth;

    @Override
    public DiagramLayoutType getDiagramType() {
        return DiagramLayoutType.NETWORK_AREA;
    }
}
