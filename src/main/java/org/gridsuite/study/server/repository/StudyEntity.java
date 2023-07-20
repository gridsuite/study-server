/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import lombok.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;

import javax.persistence.*;
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

    @Column(name = "networkUuid", nullable = false)
    private UUID networkUuid;

    @Column(name = "networkId", nullable = false)
    private String networkId;

    @Column(name = "caseFormat", nullable = false)
    private String caseFormat;

    @Column(name = "caseUuid", nullable = false)
    private UUID caseUuid;

    @Column(name = "caseName", nullable = false)
    private String caseName;

    @Column(name = "loadFlowProvider")
    private String loadFlowProvider;

    @Column(name = "securityAnalysisProvider")
    private String securityAnalysisProvider;

    @Column(name = "sensitivityAnalysisProvider")
    private String sensitivityAnalysisProvider;

    @Column(name = "dynamicSimulationProvider")
    private String dynamicSimulationProvider;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "loadFlowParametersEntity_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(
                    name = "loadFlowParameters_id_fk"
            ), nullable = false)
    private LoadFlowParametersEntity loadFlowParameters;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "shortCircuitParametersEntity_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(
                    name = "shortCircuitParameters_id_fk"
            ))
    private ShortCircuitParametersEntity shortCircuitParameters;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "dynamicSimulationParametersEntity_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(
                    name = "dynamicSimulationParameters_id_fk"
            ))
    private DynamicSimulationParametersEntity dynamicSimulationParameters;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "voltageInitParametersEntity_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(
                    name = "voltageInitParameters_id_fk"
            ))
    private VoltageInitParametersEntity voltageInitParameters;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "securityAnalysisParametersEntity_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(
                    name = "securityAnalysisParameters_id_fk"
            ))
    private SecurityAnalysisParametersEntity securityAnalysisParameters;

    @ElementCollection
    @CollectionTable(name = "importParameters",
            indexes = {@Index(name = "studyEntity_importParameters_idx1", columnList = "study_entity_id")},
            foreignKey = @ForeignKey(name = "studyEntity_importParameters_fk1"))
    private Map<String, String> importParameters;

    public ShortCircuitParametersEntity getShortCircuitParameters() {
        if (this.shortCircuitParameters == null) {
            this.setShortCircuitParameters(ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters()));
        }
        return this.shortCircuitParameters;
    }

    @Value
    public static class StudyNetworkUuid {
        UUID networkUuid;
    }
}

