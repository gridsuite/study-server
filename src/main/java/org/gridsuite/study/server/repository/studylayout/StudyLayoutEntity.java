package org.gridsuite.study.server.repository.studylayout;

import jakarta.persistence.*;
import org.gridsuite.study.server.dto.studylayout.StudyLayout;
import org.gridsuite.study.server.repository.StudyEntity;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@IdClass(StudyLayoutKey.class)
public class StudyLayoutEntity {
    @Id
    UUID studyUuid;
    @Id
    String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studyUuid", insertable = false, updatable = false)
    private StudyEntity studyEntity;

    @OneToMany
    List<AbstractDiagramParamsEntity> diagramGridLayoutEntityList;

    public StudyLayout toDto() {
        return StudyLayout.builder()
            .diagramGridLayout(diagramGridLayoutEntityList.stream().map(AbstractDiagramParamsEntity::toDiagramPositionDto).collect(Collectors.toList()))
            .diagramParams(diagramGridLayoutEntityList.stream().map(AbstractDiagramParamsEntity::toDiagramParamsDto).collect(Collectors.toList()))
            .build();
    }
}
