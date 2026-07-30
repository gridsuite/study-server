/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import lombok.Getter;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.common.ComputationParameters;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public abstract class AbstractComputationService {
    private final StudyRepository studyRepository;
    private final ComputationParametersService computationParametersService;
    @Getter
    private final NotificationService notificationService;
    @Getter
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;

    protected AbstractComputationService(StudyRepository studyRepository,
                                         ComputationParametersService computationParametersService,
                                         NotificationService notificationService,
                                         RootNetworkNodeInfoService rootNetworkNodeInfoService) {
        this.studyRepository = studyRepository;
        this.computationParametersService = computationParametersService;
        this.notificationService = notificationService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
    }

    protected StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Study not found"));
    }

    protected <T> boolean setComputationParameters(UUID studyUuid, T parameters, String userId,
                                                 Function<StudyEntity, UUID> studyParameterGetter,
                                                 BiConsumer<StudyEntity, UUID> studyParameterSetter,
                                                 Function<UserProfileInfos, UUID> profileParameterGetter,
                                                 ComputationParameters computationParameters,
                                                 Function<T, UUID> createParameters,
                                                 BiConsumer<UUID, T> updateParameters,
                                                 ComputationType computationType,
                                                 List<Consumer<UUID>> statusInvalidations,
                                                 String... statusUpdateTypes) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean userProfileIssue = computationParametersService.createOrUpdateParameters(
                studyEntity,
                parameters,
                userId,
                studyParameterGetter,
                studyParameterSetter,
                profileParameterGetter,
                computationParameters,
                createParameters,
                updateParameters,
                computationType.getLabel()
        );
        emitComputationParametersChanged(studyUuid, userId, computationType, statusInvalidations, statusUpdateTypes);
        return userProfileIssue;
    }

    private void emitComputationParametersChanged(UUID studyUuid, String userId,
                                                  ComputationType computationType,
                                                  List<Consumer<UUID>> statusInvalidations,
                                                  String... statusUpdateTypes) {
        statusInvalidations.forEach(invalidate -> invalidate.accept(studyUuid));
        Arrays.stream(statusUpdateTypes)
                .forEach(updateType -> notificationService.emitStudyChanged(studyUuid, null, null, updateType));
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, computationType);
    }

    protected void updateComputationResultUuid(UUID nodeUuid, UUID rootNetworkUuid, UUID computationResultUuid, ComputationType computationType) {
        rootNetworkNodeInfoService.updateComputationResultUuid(nodeUuid, rootNetworkUuid, computationResultUuid, computationType);
    }
}
