package org.gridsuite.study.server.dto;

import lombok.Data;

import java.util.Map;

@Data
public class InfoTypesParameters {
    Map<String, String> additionalParams;

    public String getOperation() {
        if (additionalParams == null) {
            return null;
        }
        return additionalParams.getOrDefault("operation", null);
    }

    public String getInfoType() {
        if (additionalParams == null) {
            return null;
        }
        return additionalParams.getOrDefault("infoType", null);
    }
}
