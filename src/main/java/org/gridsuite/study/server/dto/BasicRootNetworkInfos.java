package org.gridsuite.study.server.dto;

import java.util.UUID;

public record BasicRootNetworkInfos(UUID rootNetworkUuid, String name, boolean isCreating) { }
