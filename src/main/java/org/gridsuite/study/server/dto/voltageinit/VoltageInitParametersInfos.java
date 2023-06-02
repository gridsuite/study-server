package org.gridsuite.study.server.dto.voltageinit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class VoltageInitParametersInfos {
    List<VoltageInitVoltageLimitsParameterInfos> voltageLimits;
}
