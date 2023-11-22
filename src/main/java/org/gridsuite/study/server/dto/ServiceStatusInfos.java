package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ServiceStatusInfos {
    private String name;
    private String status;
}
