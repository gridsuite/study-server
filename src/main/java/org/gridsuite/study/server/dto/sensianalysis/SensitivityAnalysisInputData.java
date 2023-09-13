/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sensianalysis;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Sensitivity analysis input data")
public class SensitivityAnalysisInputData {
    public enum DistributionType {
        PROPORTIONAL,
        PROPORTIONAL_MAXP,
        REGULAR,
        VENTILATION
    }

    public enum SensitivityType {
        DELTA_MW,
        DELTA_A
    }

    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Ident {
        UUID id;
        String name;
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to injections set")
    public static class SensitivityInjectionsSet {
        List<FilterEquipments> monitoredBranches;
        List<FilterEquipments> injections;
        DistributionType distributionType;
        List<FilterEquipments> contingencies;

        public SensitivityInjectionsSet(List<FilterEquipments> monitoredBranches,
                                        List<FilterEquipments> injections,
                                        DistributionType distributionType,
                                        List<FilterEquipments> contingencies) {
            this.monitoredBranches = monitoredBranches;
            this.injections = injections;
            this.distributionType = distributionType;
            this.contingencies = contingencies;
        }
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to each injection")
    public static class SensitivityInjection {
        List<FilterEquipments> monitoredBranches;
        List<FilterEquipments> injections;
        List<FilterEquipments> contingencies;

        public SensitivityInjection(List<FilterEquipments> monitoredBranches,
                                        List<FilterEquipments> injections,
                                        List<FilterEquipments> contingencies) {
            this.monitoredBranches = monitoredBranches;
            this.injections = injections;
            this.contingencies = contingencies;
        }
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to each HVDC")
    public static class SensitivityHVDC {
        List<FilterEquipments> monitoredBranches;
        SensitivityType sensitivityType;
        List<FilterEquipments> hvdcs;
        List<FilterEquipments> contingencies;

        public SensitivityHVDC(List<FilterEquipments> monitoredBranches,
                                        List<FilterEquipments> hvdcs,
                                        SensitivityType sensitivityType,
                                        List<FilterEquipments> contingencies) {
            this.monitoredBranches = monitoredBranches;
            this.hvdcs = hvdcs;
            this.sensitivityType = sensitivityType;
            this.contingencies = contingencies;
        }
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to each PST")
    public static class SensitivityPST {
        List<FilterEquipments> monitoredBranches;
        SensitivityType sensitivityType;
        List<FilterEquipments> psts;
        List<FilterEquipments> contingencies;

        public SensitivityPST(List<FilterEquipments> monitoredBranches,
                               List<FilterEquipments> psts,
                               SensitivityType sensitivityType,
                               List<FilterEquipments> contingencies) {
            this.monitoredBranches = monitoredBranches;
            this.psts = psts;
            this.sensitivityType = sensitivityType;
            this.contingencies = contingencies;
        }
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to nodes")
    public static class SensitivityNodes {
        List<FilterEquipments> monitoredVoltageLevels;
        List<FilterEquipments> equipmentsInVoltageRegulation;
        List<FilterEquipments> contingencies;

        public SensitivityNodes(List<FilterEquipments> monitoredVoltageLevels,
                                    List<FilterEquipments> equipmentsInVoltageRegulation,
                                    List<FilterEquipments> contingencies) {
            this.monitoredVoltageLevels = monitoredVoltageLevels;
            this.equipmentsInVoltageRegulation = equipmentsInVoltageRegulation;
            this.contingencies = contingencies;
        }
    }

    @Schema(description = "Results threshold")
    private double resultsThreshold;

    @Schema(description = "Sensitivity relatively to injections set")
    private List<SensitivityInjectionsSet> sensitivityInjectionsSets;

    @Schema(description = "Sensitivity relatively to each injection")
    private List<SensitivityInjection> sensitivityInjections;

    @Schema(description = "Sensitivity relatively to each HVDC")
    private List<SensitivityHVDC> sensitivityHVDCs;

    @Schema(description = "Sensitivity relatively to each PST")
    private List<SensitivityPST> sensitivityPSTs;

    @Schema(description = "Sensitivity relatively to nodes")
    private List<SensitivityNodes> sensitivityNodes;

    @Schema(description = "Sensitivity parameters")
    private SensitivityAnalysisParameters parameters;

    @Schema(description = "Loadflow model-specific parameters")
    private Map<String, String> loadFlowSpecificParameters;
}
