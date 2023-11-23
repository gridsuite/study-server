package org.gridsuite.study.server.dto;

import lombok.Builder;

@Builder
public record ServiceStatusInfos(String name, ServiceStatus status) {
    public enum ServiceStatus {
        DOWN, UP
    }
}
