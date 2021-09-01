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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A class to implement metadatas transfer in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class StudyInfosServiceImpl implements StudyInfosService {

    @Autowired
    private StudyInfosRepository studyInfosRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

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
        SearchHits<CreatedStudyBasicInfos> searchHits = elasticsearchOperations.search(new NativeSearchQuery(QueryBuilders.queryStringQuery(query)), CreatedStudyBasicInfos.class);
        return searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
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
