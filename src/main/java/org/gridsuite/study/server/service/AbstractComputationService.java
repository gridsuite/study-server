/**
 * Copyright (c) 20226, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public abstract class AbstractComputationService {
    private final StudyRepository studyRepository;

    public AbstractComputationService(StudyRepository studyRepository) {
        this.studyRepository = studyRepository;
    }

    protected StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Study not found"));
    }
}
