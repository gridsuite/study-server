/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.elasticsearch.index.query.QueryBuilders;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A class to implement indexing in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class StudyInfosServiceImpl implements StudyInfosService {

    private final StudyInfosRepository studyInfosRepository;

    private final ElasticsearchOperations elasticsearchOperations;

    public StudyInfosServiceImpl(StudyInfosRepository studyInfosRepository, ElasticsearchOperations elasticsearchOperations) {
        this.studyInfosRepository = studyInfosRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public CreatedStudyBasicInfos add(@NonNull final CreatedStudyBasicInfos ci) {
        studyInfosRepository.save(ci);
        return ci;
    }

    @Override
    public Iterable<CreatedStudyBasicInfos> findAll() {
        return studyInfosRepository.findAll();
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
    public void recreateStudyInfos(@NonNull final CreatedStudyBasicInfos studyInfos) {
        studyInfosRepository.deleteById(studyInfos.getId());
        studyInfosRepository.save(studyInfos);
    }
}
