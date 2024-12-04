package org.gridsuite.study.server.repository.rootnetwork;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.RootNetworkCreationRequestInfos;

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

    private UUID studyUuid;

    private String userId;

    public RootNetworkCreationRequestInfos toDto() {
        return RootNetworkCreationRequestInfos.builder()
            .id(this.getId())
            .studyUuid(this.getStudyUuid())
            .userId(this.getUserId())
            .build();
    }
}
