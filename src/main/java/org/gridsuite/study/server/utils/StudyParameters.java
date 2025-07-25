package org.gridsuite.study.server.utils;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
public class StudyParameters {

    private final UUID rootNetworkUuid;
    private final UUID nodeUuid;
    @Setter
    private String variantId = null;
    @Setter
    private UUID networkUuid = null;
    @Setter
    private UUID resultUuid = null;

    public StudyParameters(UUID rootNetworkUuid, UUID nodeUuid) {
        this.rootNetworkUuid = rootNetworkUuid;
        this.nodeUuid = nodeUuid;
    }
}
