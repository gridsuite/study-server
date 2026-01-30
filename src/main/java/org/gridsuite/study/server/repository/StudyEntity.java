/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.study.server.dto.RootNetworkIndexationStatus;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;

import java.util.*;

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

    @OneToMany(mappedBy = "study", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "index")
    @Builder.Default
    private List<RootNetworkEntity> rootNetworks = new ArrayList<>();

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

    @Column(name = "dynamicSecurityAnalysisParametersUuid")
    private UUID dynamicSecurityAnalysisParametersUuid;

    @Column(name = "voltageInitParametersUuid")
    private UUID voltageInitParametersUuid;

    @Column(name = "securityAnalysisParametersUuid")
    private UUID securityAnalysisParametersUuid;

    @Column(name = "sensitivityAnalysisParametersUuid")
    private UUID sensitivityAnalysisParametersUuid;

    @Column(name = "stateEstimationParametersUuid")
    private UUID stateEstimationParametersUuid;

    @Column(name = "pccMinParametersUuid")
    private UUID pccMinParametersUuid;

    @Column(name = "networkVisualizationParametersUuid")
    private UUID networkVisualizationParametersUuid;

    @Column(name = "spreadsheetConfigCollectionUuid")
    private UUID spreadsheetConfigCollectionUuid;

    @Column(name = "workspacesConfigUuid")
    private UUID workspacesConfigUuid;

    @Column(name = "computationResultFiltersUuid")
    private UUID computationResultFiltersUuid;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "study_voltage_init_parameters_id",
        foreignKey = @ForeignKey(
            name = "study_voltage_init_parameters_id_fk"
        ))
    private StudyVoltageInitParametersEntity voltageInitParameters;

    @ElementCollection
    @CollectionTable(name = "StudyNodeAliases", foreignKey = @ForeignKey(
            name = "study_node_aliases_fk"
        ))
    private List<NodeAliasEmbeddable> nodeAliases;

    @ElementCollection
    @CollectionTable(name = "StudyNadConfigs", foreignKey = @ForeignKey(
            name = "study_nad_configs_fk"
        ))
    @Column(name = "nad_config_uuid")
    @Builder.Default
    private Set<UUID> nadConfigsUuids = new HashSet<>();

    @Column(name = "mono_root", columnDefinition = "boolean default true")
    private boolean monoRoot;

    @Embedded
    @Builder.Default
    private SpreadsheetParametersEntity spreadsheetParameters = new SpreadsheetParametersEntity();

    public RootNetworkEntity getFirstRootNetwork() {
        return rootNetworks.get(0);
    }

    public void addRootNetwork(RootNetworkEntity rootNetworkEntity) {
        rootNetworkEntity.setStudy(this);
        rootNetworkEntity.setIndexationStatus(RootNetworkIndexationStatus.INDEXED);
        rootNetworks.add(rootNetworkEntity);
    }

    public void deleteRootNetworks(Set<UUID> uuids) {
        rootNetworks.removeAll(rootNetworks.stream().filter(rn -> uuids.contains(rn.getId())).toList());
    }
}
