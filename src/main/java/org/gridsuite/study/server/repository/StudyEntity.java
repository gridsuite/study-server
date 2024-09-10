/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.study.server.dto.StudyIndexationStatus;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "study")
public class StudyEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> implements BasicStudyEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @OneToMany(mappedBy = "study")  // Can define 'cascade = CascadeType.ALL' here as well instead of in Product entity
    private List<TimePointEntity> timePoints;

    /**
     * @deprecated to remove when the data is migrated into the loadflow-server
     */
    @Deprecated(forRemoval = true, since = "1.3.0")
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    @Column(name = "loadFlowProvider")
    private String loadFlowProvider;

    /**
     * @deprecated to remove when the data is migrated into the security-analysis-server
     */
    @Deprecated(forRemoval = true, since = "1.3.0")
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    @Column(name = "securityAnalysisProvider")
    private String securityAnalysisProvider;

    /**
     * @deprecated to remove when the data is migrated into the sensitivity-analysis-server
     */
    @Deprecated(forRemoval = true, since = "1.4.0")
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    @Column(name = "sensitivityAnalysisProvider")
    private String sensitivityAnalysisProvider;

    @Column(name = "nonEvacuatedEnergyProvider")
    private String nonEvacuatedEnergyProvider;

    @Column(name = "dynamicSimulationProvider")
    private String dynamicSimulationProvider;

    @Column(name = "loadFlowParametersUuid")
    private UUID loadFlowParametersUuid;

    @Column(name = "shortCircuitParametersUuid")
    private UUID shortCircuitParametersUuid;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "dynamicSimulationParametersEntity_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(
                    name = "dynamicSimulationParameters_id_fk"
            ))
    private DynamicSimulationParametersEntity dynamicSimulationParameters;

    @Column(name = "voltageInitParametersUuid")
    private UUID voltageInitParametersUuid;

    @Column(name = "securityAnalysisParametersUuid")
    private UUID securityAnalysisParametersUuid;

    @Column(name = "sensitivityAnalysisParametersUuid")
    private UUID sensitivityAnalysisParametersUuid;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "nonEvacuatedEnergyParametersEntity_id",
        referencedColumnName = "id",
        foreignKey = @ForeignKey(
            name = "nonEvacuatedEnergyParameters_id_fk"
        ))
    private NonEvacuatedEnergyParametersEntity nonEvacuatedEnergyParameters;

    @ElementCollection
    @CollectionTable(name = "importParameters",
            indexes = {@Index(name = "studyEntity_importParameters_idx1", columnList = "study_entity_id")},
            foreignKey = @ForeignKey(name = "studyEntity_importParameters_fk1"))
    private Map<String, String> importParameters;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StudyIndexationStatus indexationStatus = StudyIndexationStatus.NOT_INDEXED;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "study_voltage_init_parameters_id",
        foreignKey = @ForeignKey(
            name = "study_voltage_init_parameters_id_fk"
        ))
    private StudyVoltageInitParametersEntity voltageInitParameters;

    @Value
    public static class StudyNetworkUuid {
        UUID networkUuid;
    }

    //TODO temporary, for now we are only working with one timepoint
    public TimePointEntity getFirstTimepoint() {
        if (timePoints == null || timePoints.isEmpty()) {
            return TimePointEntity.builder().build();
        }
        return timePoints.get(0);
    }
}

