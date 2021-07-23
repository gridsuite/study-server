/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.springframework.lang.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An interface to define an api for metadatas transfer in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class StudyInfosServiceMock implements StudyInfosService {

    @Override
    public CreatedStudyBasicInfos addStudyInfos(@NonNull final CreatedStudyBasicInfos si) {
        return si;
    }

    @Override
    public List<CreatedStudyBasicInfos> getAllStudyInfos() {
        return Collections.emptyList();
    }

    @Override
    public Optional<CreatedStudyBasicInfos> getStudyInfosByUuid(@NonNull final UUID uuid) {
        return Optional.empty();
    }

    @Override
    public List<CreatedStudyBasicInfos> searchStudyInfos(@NonNull final String query) {
        return Collections.emptyList();
    }

    @Override
    public void deleteStudyInfos(@NonNull final CreatedStudyBasicInfos si) {
    }

    @Override
    public void deleteStudyInfosByUuid(@NonNull final UUID uuid) {
    }

    @Override
    public void deleteAllStudyInfos() {
    }
}
