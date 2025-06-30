package org.gridsuite.study.server.dto.studylayout.diagramlayout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SuperBuilder
public abstract class AbstractDiagramParams {
    UUID diagramUuid;

    @JsonIgnore
    public abstract DiagramLayoutType getDiagramType();

    public String getType() {
        return getDiagramType().getLabel();
    }
}
