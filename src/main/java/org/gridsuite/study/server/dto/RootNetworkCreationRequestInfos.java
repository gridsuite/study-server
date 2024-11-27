package org.gridsuite.study.server.dto;

import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestEntity;

import java.util.UUID;

@Builder
@Getter
public class RootNetworkCreationRequestInfos {
    @Id
    private UUID id;

    private UUID studyUuid;

    private String userId;
}
