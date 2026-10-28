/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import lombok.NonNull;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.nodeactivity.BatchActionRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_MODIFICATIONS;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public class BatchActionRunnerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchActionRunnerService.class);

    private final NetworkModificationTreeService networkModificationTreeService;
    private final BatchActionRepository batchActionRepository;

    public BatchActionRunnerService(NetworkModificationTreeService networkModificationTreeService,
                                    BatchActionRepository batchActionRepository) {
        this.networkModificationTreeService = networkModificationTreeService;
        this.batchActionRepository = batchActionRepository;
    }

    public void runLastAction(AbstractNodeActivityRunnerService activityRunner) {
        // Get most recent batch
        // TODO use a query with oldest instant
        BatchActionEntity mostRecentBatch = batchActionRepository.findAll().stream()
            .max(Comparator.comparing(BatchActionEntity::getCreatedAt))
            .orElse(null);
        if (mostRecentBatch == null) {
            return;
        }

        // Delete batch in DB (automatically saved if run failed)
        batchActionRepository.deleteById(mostRecentBatch.getId());

        // Run batch
        switch (mostRecentBatch.getType()) {
            case INVALIDATE_NODE_TREE:
                runAction(activityRunner, mostRecentBatch);
                break;
            default:
                LOGGER.error("Unsupported batch node action type : {}", mostRecentBatch.getType());
        }
    }

    public void runAction(@NonNull AbstractNodeActivityRunnerService activityRunner, @NonNull BatchActionEntity batchAction) {
        try {
            runAction(batchAction, activityRunner);
        } catch (StudyException e) {
            if (!e.isNodeActivityConflict()) {
                throw e;
            }
            batchActionRepository.save(batchAction);
        }
    }

    private void runAction(BatchActionEntity batchAction, AbstractNodeActivityRunnerService activityRunner) {
        switch (batchAction.getType()) {
            case INVALIDATE_NODE_TREE:
                activityRunner.runWith(EDIT_MODIFICATIONS, batchAction.getStudyId(), null, List.of(batchAction.getNodeId()),
                    () -> networkModificationTreeService.invalidateNodeTree(batchAction.getStudyId(), batchAction.getNodeId()));
                break;
            default:
                LOGGER.error("Unsupported batch node action type : {}", batchAction.getType());
        }
    }
}
