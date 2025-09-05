package org.gridsuite.study.server.utils;

import lombok.Getter;

import java.util.UUID;

@Getter
public final class ResultParameters {

    private final UUID rootNetworkUuid;
    private final UUID nodeUuid;
    private final String variantId;
    private final UUID networkUuid;
    private final UUID resultUuid;

    /**
     * Full constructor
     */
    public ResultParameters(UUID rootNetworkUuid, UUID nodeUuid, String variantId, UUID networkUuid, UUID resultUuid) {
        this.rootNetworkUuid = rootNetworkUuid;
        this.nodeUuid = nodeUuid;
        this.variantId = variantId;
        this.networkUuid = networkUuid;
        this.resultUuid = resultUuid;
    }

    /**
     * Minimal constructor
     */
    public ResultParameters(UUID rootNetworkUuid, UUID nodeUuid) {
        this(rootNetworkUuid, nodeUuid, null, null, null);
    }
}
