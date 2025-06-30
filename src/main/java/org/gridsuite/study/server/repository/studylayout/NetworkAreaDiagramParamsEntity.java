package org.gridsuite.study.server.repository.studylayout;

import jakarta.persistence.Entity;
import org.gridsuite.study.server.dto.studylayout.diagramlayout.NetworkAreaDiagramParams;

import java.util.List;

@Entity
public class NetworkAreaDiagramParamsEntity extends AbstractDiagramParamsEntity {
    List<String> voltageLevelIds;
    Integer depth;

    public NetworkAreaDiagramParams toDiagramParamsDto() {
        return NetworkAreaDiagramParams.builder()
            .diagramUuid(diagramUuid)
            .depth(depth)
            .voltageLevelIds(voltageLevelIds)
            .build();
    }
}
