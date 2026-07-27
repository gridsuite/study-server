/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.studyexport.StudyImportContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary in-memory storage for StudyImportContext during async study import.
 * Stores the context between the import trigger and the consumer callback.
 *
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class StudyImportContextService {

    private final Map<UUID, StudyImportContext> importContexts = new ConcurrentHashMap<>();

    /**
     * Store StudyImportContext for a study import operation
     * @param studyUuid The study UUID
     * @param importContext The import context to store
     * @return The context key (same as studyUuid)
     */
    public UUID storeImportContext(UUID studyUuid, StudyImportContext importContext) {
        importContexts.put(studyUuid, importContext);
        return studyUuid;
    }

    /**
     * Retrieve and remove StudyImportContext for a study import operation
     * @param studyUuid The study UUID
     * @return The stored import context, or null if not found
     */
    public StudyImportContext getAndRemoveImportContext(UUID studyUuid) {
        return importContexts.remove(studyUuid);
    }

    /**
     * Remove import context without returning it
     * @param studyUuid The study UUID
     */
    public void removeImportContext(UUID studyUuid) {
        importContexts.remove(studyUuid);
    }
}
