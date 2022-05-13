/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
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

    void deleteByUuid(@NonNull final UUID uuid);

    Iterable<CreatedStudyBasicInfos> findAll();

    List<CreatedStudyBasicInfos> search(@NonNull final String query);

    void recreateStudyInfos(@NonNull final CreatedStudyBasicInfos studyInfos);

    static String getDateSearchTerm(@NonNull final ZonedDateTime... dates) {
        return Arrays.stream(dates).map(date -> "\"" + date.toString() + "\"").collect(Collectors.joining(" OR ", "creationDate:", ""));
    }
}
