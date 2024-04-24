package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class InfoTypesParameters {
    private Map<String, String> additionalParams;
}
