/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.studyexport.StudyImportContext;
import org.gridsuite.study.server.repository.StudyCreationRequestEntity;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Shared storage for StudyImportContext during async study import.
 * Stores the context between the import trigger and the consumer callback.
 *
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class StudyImportContextService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyImportContextService.class);

    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final ObjectMapper objectMapper;

    public StudyImportContextService(StudyCreationRequestRepository studyCreationRequestRepository,
                                      ObjectMapper objectMapper) {
        this.studyCreationRequestRepository = studyCreationRequestRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID storeImportContext(UUID studyUuid, StudyImportContext importContext) {
        try {
            String serializedContext = objectMapper.writeValueAsString(importContext);
            StudyCreationRequestEntity entity = studyCreationRequestRepository.findById(studyUuid)
                    .orElseThrow(() -> new IllegalStateException("No pending creation request for study '" + studyUuid + "'"));
            entity.setImportContext(serializedContext);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return studyUuid;
    }

    /**
     * Retrieve and clear the StudyImportContext for a study import operation
     * @param studyUuid The study UUID
     * @return The stored import context, or null if not found or expired
     */
    @Transactional
    public StudyImportContext getAndRemoveImportContext(UUID studyUuid) {
        StudyImportContext importContext = readIfNotExpired(studyUuid);
        clearImportContext(studyUuid);
        return importContext;
    }

    /**
     * Clear the import context without returning it
     * @param studyUuid The study UUID
     */
    @Transactional
    public void removeImportContext(UUID studyUuid) {
        clearImportContext(studyUuid);
    }

    private void clearImportContext(UUID studyUuid) {
        studyCreationRequestRepository.findById(studyUuid).ifPresent(entity -> {
            entity.setImportContext(null);
        });
    }

    private StudyImportContext readIfNotExpired(UUID studyUuid) {
        return studyCreationRequestRepository.findById(studyUuid)
                .filter(entity -> entity.getImportContext() != null)
                .map(entity -> {
                    try {
                        return objectMapper.readValue(entity.getImportContext(), StudyImportContext.class);
                    } catch (JsonProcessingException e) {
                        LOGGER.error("Error while deserializing StudyImportContext for study '{}'", studyUuid, e);
                        return null;
                    }
                })
                .orElse(null);
    }
}
