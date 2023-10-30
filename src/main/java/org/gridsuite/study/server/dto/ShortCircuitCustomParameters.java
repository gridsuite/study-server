package org.gridsuite.study.server.dto;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.StudyType;
import com.powsybl.shortcircuit.VoltageRange;
import lombok.*;

import java.util.List;


/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ShortCircuitCustomParameters {
    private String version;
    private boolean withLimitViolations;
    private boolean withFortescueResult;
    private boolean withFeederResult;
    private StudyType studyType;
    private boolean withVoltageResult;
    private double minVoltageDropProportionalThreshold;
    private boolean withLoads;
    private boolean withShuntCompensators;
    private boolean withVSCConverterStations;
    private boolean withNeutralPosition;
    private InitialVoltageProfileMode initialVoltageProfileMode;
    private List<VoltageRange> voltageRanges;
    private ShortCircuitPredefinedParametersType predefinedParameters;
}
