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
 * A class to mock metadatas transfer in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class StudyInfosServiceMock implements StudyInfosService {

    @Override
    public CreatedStudyBasicInfos add(@NonNull final CreatedStudyBasicInfos si) {
        return si;
    }

    @Override
    public Optional<CreatedStudyBasicInfos> getByUuid(@NonNull final UUID uuid) {
        return Optional.empty();
    }

    @Override
    public List<CreatedStudyBasicInfos> search(@NonNull final String query) {
        return Collections.emptyList();
    }

    @Override
    public void deleteByUuid(@NonNull final UUID uuid) {
        // Nothing to delete
    }
}
