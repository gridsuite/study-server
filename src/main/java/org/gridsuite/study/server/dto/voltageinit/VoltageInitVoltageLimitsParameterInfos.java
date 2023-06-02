package org.gridsuite.study.server.dto.voltageinit;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class VoltageInitVoltageLimitsParameterInfos {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Integer priority;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Double lowVoltageLimit;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Double highVoltageLimit;

    List<FilterEquipments> filters;
}
