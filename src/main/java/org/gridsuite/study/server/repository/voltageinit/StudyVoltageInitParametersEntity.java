/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.voltageinit;

import jakarta.persistence.*;
import lombok.Setter;

import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Setter
@Entity
@Table(name = "study_voltage_init_parameters")
public class StudyVoltageInitParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "apply_modifications")
    private boolean applyModifications;

    public StudyVoltageInitParametersEntity() {
        this.applyModifications = true;
    }

    public StudyVoltageInitParametersEntity(boolean applyModifications) {
        this.applyModifications = applyModifications;
    }

    public boolean shouldApplyModifications() {
        return applyModifications;
    }
}
