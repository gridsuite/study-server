package org.gridsuite.study.server.dto;

import java.util.UUID;

public record BasicRootNetworkInfos(UUID rootNetworkUuid, UUID originalCaseUuid, String name, String tag, boolean isCreating) { }
