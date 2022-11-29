/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;
import org.gridsuite.study.server.service.ShortCircuitService;

import javax.persistence.*;

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

    @Column(name = "creationDate", nullable = false)
    private LocalDateTime date;

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

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "loadFlowParametersEntity_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(
                    name = "loadFlowParameters_id_fk"
            ), nullable = false)
    private LoadFlowParametersEntity loadFlowParameters;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name  =  "shortCircuitParametersEntity_id",
            referencedColumnName  =  "id",
            foreignKey = @ForeignKey(
                    name = "shortCircuitParameters_id_fk"
            ))
    private ShortCircuitParametersEntity shortCircuitParameters;

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

