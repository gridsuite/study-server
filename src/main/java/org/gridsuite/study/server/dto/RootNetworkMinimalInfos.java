package org.gridsuite.study.server.dto;

import java.util.UUID;

public record RootNetworkMinimalInfos(UUID rootNetworkUuid, String name, boolean isCreating) { }
