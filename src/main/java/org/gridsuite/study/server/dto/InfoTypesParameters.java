package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@Data
@Builder
public class InfoTypesParameters {
    @Value("#{${additionalParams: {}}}")
    private Map<String, String> additionalParams;
}
