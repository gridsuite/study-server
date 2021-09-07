/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.joda.time.DateTime;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An interface to define an api for metadatas transfer in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public interface StudyInfosService {

    CreatedStudyBasicInfos add(@NonNull final CreatedStudyBasicInfos ci);

    Optional<CreatedStudyBasicInfos> getByUuid(@NonNull final UUID uuid);

    List<CreatedStudyBasicInfos> search(@NonNull final String query);

    void deleteByUuid(@NonNull final UUID uuid);

    static String getDateSearchTerm(@NonNull final DateTime... dates) {
        return Arrays.stream(dates).map(date -> "\"" + date.toDateTimeISO() + "\"").collect(Collectors.joining(" OR ", "date:", ""));
    }

}
