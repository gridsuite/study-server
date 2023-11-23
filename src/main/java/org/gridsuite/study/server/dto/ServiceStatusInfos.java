package org.gridsuite.study.server.dto;

import lombok.Builder;
import org.gridsuite.study.server.service.client.RemoteServiceName;

@Builder
public record ServiceStatusInfos(String name, ServiceStatus status) {
    public ServiceStatusInfos(RemoteServiceName name, ServiceStatus status) {
        this(name.serviceName(), status);
    }

    public enum ServiceStatus {
        DOWN, UP
    }
}
