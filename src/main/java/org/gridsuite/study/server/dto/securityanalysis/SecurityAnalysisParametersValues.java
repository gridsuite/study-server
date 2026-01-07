package org.gridsuite.study.server.dto.securityanalysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.LimitReductionsByVoltageLevel;

import java.util.List;

/**
 * This DTO is a copy of Security Analysis Server's SecurityAnalysisParametersValues DTO and require updates when original DTO is updated
 *
 * @author Kamil MARUT {@literal <kamil.marut at rte-france.com>}
 */

@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SecurityAnalysisParametersValues {

    private String provider;

    private double lowVoltageAbsoluteThreshold;

    private double lowVoltageProportionalThreshold;

    private double highVoltageAbsoluteThreshold;

    private double highVoltageProportionalThreshold;

    private double flowProportionalThreshold;

    private List<LimitReductionsByVoltageLevel> limitReductions;

}
