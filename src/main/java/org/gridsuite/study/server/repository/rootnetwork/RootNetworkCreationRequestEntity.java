package org.gridsuite.study.server.repository.rootnetwork;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.RootNetworkCreationRequestInfos;
import org.gridsuite.study.server.repository.StudyEntity;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rootNetworkCreationRequest")
public class RootNetworkCreationRequestEntity {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "study_entity_id", foreignKey = @ForeignKey(name = "rootNetworkCreationRequest_study_id_fk_constraint"))
    private StudyEntity studyEntity;

    private String userId;

    public RootNetworkCreationRequestInfos toDto() {
        return RootNetworkCreationRequestInfos.builder()
            .id(this.getId())
            .studyUuid(this.getStudyEntity().getId())
            .userId(this.getUserId())
            .build();
    }
}
