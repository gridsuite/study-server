/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import com.google.common.collect.Lists;
import org.elasticsearch.index.query.QueryBuilders;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A class to implement metadatas transfer in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class StudyInfosServiceImpl implements StudyInfosService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyInfosServiceImpl.class);

    @Autowired
    private StudyInfosRepository studyInfosRepository;

    @Override
    public CreatedStudyBasicInfos add(@NonNull final CreatedStudyBasicInfos ci) {
        studyInfosRepository.save(ci);
        return ci;
    }

    @Override
    public Optional<CreatedStudyBasicInfos> getByUuid(@NonNull final UUID uuid) {
        Page<CreatedStudyBasicInfos> res = studyInfosRepository.findByStudyUuid(uuid, PageRequest.of(0, 1));
        return res.get().findFirst();
    }

    @Override
    public List<CreatedStudyBasicInfos> getAll() {
        return Lists.newArrayList(studyInfosRepository.findAll());
    }

    @Override
    public List<CreatedStudyBasicInfos> search(@NonNull final String query) {
        return Lists.newArrayList(studyInfosRepository.search(QueryBuilders.queryStringQuery(query)));
    }

    @Override
    public void deleteByUuid(@NonNull UUID uuid) {
        studyInfosRepository.deleteById(uuid);
    }

    @Override
    public void deleteAll() {
        studyInfosRepository.deleteAll(getAll());
    }
}
