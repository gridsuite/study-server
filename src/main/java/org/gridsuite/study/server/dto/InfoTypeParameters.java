package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class InfoTypeParameters {
    public static final String QUERY_PARAM_DC_POWERFACTOR = "dcPowerFactor";
    public static final String QUERY_PARAM_OPERATION = "operation";
    private String infoType;
    private Map<String, String> optionalParameters;
    public InfoTypeParameters(String infoType, Map<String, String> optionalParameters) {
        this.infoType = infoType;
        this.optionalParameters = optionalParameters == null ? new HashMap<>() : optionalParameters;
    }
}
