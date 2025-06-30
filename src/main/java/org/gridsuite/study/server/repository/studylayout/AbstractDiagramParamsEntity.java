package org.gridsuite.study.server.repository.studylayout;

import jakarta.persistence.*;
import org.gridsuite.study.server.dto.studylayout.diagramlayout.AbstractDiagramParams;
import org.gridsuite.study.server.dto.studylayout.diagramlayout.DiagramPosition;

import java.util.UUID;

@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@MappedSuperclass
public abstract class AbstractDiagramParamsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    UUID diagramUuid;
    Integer width;
    Integer height;
    Integer xPosition;
    Integer yPosition;

    public abstract AbstractDiagramParams toDiagramParamsDto();

    public DiagramPosition toDiagramPositionDto() {
        return new DiagramPosition(diagramUuid, width, height, xPosition, yPosition);
    }
}
