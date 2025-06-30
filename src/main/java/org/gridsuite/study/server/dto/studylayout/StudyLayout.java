package org.gridsuite.study.server.dto.studylayout;

import lombok.Builder;
import org.gridsuite.study.server.dto.studylayout.diagramlayout.AbstractDiagramParams;
import org.gridsuite.study.server.dto.studylayout.diagramlayout.DiagramPosition;

import java.util.List;

@Builder
public class StudyLayout {
    List<AbstractDiagramParams> diagramParams;
    List<DiagramPosition> diagramGridLayout;
}
